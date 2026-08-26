package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class EnkaGameProfileClient implements ProfileProviderClient {

    private static final Logger log = LoggerFactory.getLogger(EnkaGameProfileClient.class);

    private final RestClient restClient;
    private final Clock clock;
    private final Duration cacheDefaultTtl;
    private final int cacheMaxEntries;
    private final int maxRequestsPerSecond;
    private final int maxConcurrentRequests;
    private final int maxWaitingRequests;
    private final Duration requestWaitTimeout;
    private final Duration backoffOn429;
    private final Semaphore admissionSlots;
    private final Semaphore concurrentSlots;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> globalBackoffUntil = new AtomicReference<>(Instant.EPOCH);
    private final Object rateLock = new Object();
    private long nextRequestStartNanos;

    public EnkaGameProfileClient(
            @Qualifier("enkaRestClient") RestClient enkaRestClient,
            AppProperties properties,
            Clock clock
    ) {
        this.restClient = enkaRestClient;
        this.clock = clock;

        AppProperties.Enka enka = properties.enka();
        this.cacheDefaultTtl = positiveDuration(enka.cacheDefaultTtl(), Duration.ofSeconds(60));
        this.cacheMaxEntries = Math.max(100, enka.cacheMaxEntries());
        this.maxRequestsPerSecond = Math.max(1, enka.maxRequestsPerSecond());
        this.maxConcurrentRequests = Math.max(1, enka.maxConcurrentRequests());
        this.maxWaitingRequests = Math.max(0, enka.maxWaitingRequests());
        this.requestWaitTimeout = positiveDuration(enka.requestWaitTimeout(), Duration.ofSeconds(15));
        this.backoffOn429 = positiveDuration(enka.backoffOn429(), Duration.ofSeconds(30));

        long totalCapacity = (long) maxConcurrentRequests + maxWaitingRequests;
        int admissionCapacity = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, totalCapacity));
        this.admissionSlots = new Semaphore(admissionCapacity, true);
        this.concurrentSlots = new Semaphore(maxConcurrentRequests, true);

        log.info(
                "ENKA traffic guard configured: rps={}, concurrent={}, waiting={}, waitTimeout={}, cacheDefaultTtl={}, cacheMaxEntries={}, backoff429={}",
                maxRequestsPerSecond,
                maxConcurrentRequests,
                maxWaitingRequests,
                requestWaitTimeout,
                cacheDefaultTtl,
                cacheMaxEntries,
                backoffOn429
        );
    }

    @Override
    public ProfileProvider provider() {
        return ProfileProvider.ENKA;
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        String maskedUid = maskUid(uid);
        Instant now = clock.instant();
        CacheEntry cached = readCached(uid, now);
        if (cached != null) {
            long ttlRemaining = Math.max(0L, Duration.between(now, cached.expiresAt()).toSeconds());
            log.info("ENKA TTL cache hit uid={} ttlRemaining={}s forceUpdate={}", maskedUid, ttlRemaining, forceUpdate);
            return cached.profile();
        }

        requireBackoffExpired(maskedUid, now);

        if (!admissionSlots.tryAcquire()) {
            log.warn(
                    "ENKA local queue full uid={} activeOrWaiting={} capacity={}",
                    maskedUid,
                    admissionCapacityInUse(),
                    maxConcurrentRequests + maxWaitingRequests
            );
            throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED);
        }

        boolean concurrentAcquired = false;
        try {
            awaitRatePermit(maskedUid);
            requireBackoffExpired(maskedUid, clock.instant());

            try {
                concurrentAcquired = concurrentSlots.tryAcquire(
                        requestWaitTimeout.toNanos(),
                        TimeUnit.NANOSECONDS
                );
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED, exception);
            }

            if (!concurrentAcquired) {
                log.warn("ENKA concurrent-slot wait timed out uid={} timeout={}", maskedUid, requestWaitTimeout);
                throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED);
            }

            requireBackoffExpired(maskedUid, clock.instant());
            return requestAndCache(uid, maskedUid, forceUpdate);
        } finally {
            if (concurrentAcquired) concurrentSlots.release();
            admissionSlots.release();
        }
    }

    private PublicGameProfile requestAndCache(String uid, String maskedUid, boolean forceUpdate) {
        long startedAt = System.nanoTime();
        int inFlight = maxConcurrentRequests - concurrentSlots.availablePermits();
        log.info("ENKA request start uid={} forceUpdate={} inFlight={}/{}", maskedUid, forceUpdate, inFlight, maxConcurrentRequests);

        try {
            JsonNode root = restClient.get()
                    .uri("/api/hsr/uid/{uid}", uid)
                    .retrieve()
                    .body(JsonNode.class);
            PublicGameProfile profile = parse(root, uid);
            long ttlSeconds = cacheTtlSeconds(root);
            cache.put(uid, new CacheEntry(profile, clock.instant().plusSeconds(ttlSeconds)));
            trimCacheIfNeeded();

            log.info(
                    "ENKA request success uid={} elapsedMs={} ttl={}s characters={}",
                    maskedUid,
                    elapsedMillis(startedAt),
                    ttlSeconds,
                    profile.characters().size()
            );
            return profile;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            long elapsedMs = elapsedMillis(startedAt);
            if (status.value() == 429) {
                Duration backoff = effective429Backoff(exception);
                Instant until = extendGlobalBackoff(backoff);
                log.warn(
                        "ENKA HTTP 429 uid={} elapsedMs={} globalBackoffUntil={} backoff={}",
                        maskedUid,
                        elapsedMs,
                        until,
                        backoff
                );
                throw new AppException(ErrorCode.PROFILE_SYNC_COOLDOWN, exception);
            }
            log.warn("ENKA HTTP error uid={} status={} elapsedMs={}", maskedUid, status.value(), elapsedMs);
            if (status.value() == 400 || status.value() == 404 || status.value() == 422) {
                throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED, exception);
            }
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            Throwable cause = rootCause(exception);
            log.warn(
                    "ENKA network failure uid={} elapsedMs={} cause={} message={}",
                    maskedUid,
                    elapsedMillis(startedAt),
                    cause.getClass().getSimpleName(),
                    safeMessage(cause)
            );
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        }
    }

    private CacheEntry readCached(String uid, Instant now) {
        CacheEntry cached = cache.get(uid);
        if (cached == null) return null;
        if (now.isBefore(cached.expiresAt())) return cached;
        cache.remove(uid, cached);
        return null;
    }

    private long cacheTtlSeconds(JsonNode root) {
        long fallback = Math.max(1L, cacheDefaultTtl.toSeconds());
        if (root == null) return fallback;
        return Math.max(1L, root.path("ttl").asLong(fallback));
    }

    private void trimCacheIfNeeded() {
        if (cache.size() <= cacheMaxEntries) return;

        Instant now = clock.instant();
        cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
        while (cache.size() > cacheMaxEntries) {
            Map.Entry<String, CacheEntry> oldest = cache.entrySet().stream()
                    .min(Comparator.comparing(entry -> entry.getValue().expiresAt()))
                    .orElse(null);
            if (oldest == null) return;
            cache.remove(oldest.getKey(), oldest.getValue());
        }
    }

    private void awaitRatePermit(String maskedUid) {
        long spacingNanos = Math.max(1L, TimeUnit.SECONDS.toNanos(1) / maxRequestsPerSecond);
        long waitNanos;

        synchronized (rateLock) {
            long now = System.nanoTime();
            long scheduled = Math.max(now, nextRequestStartNanos);
            waitNanos = scheduled - now;
            if (waitNanos > requestWaitTimeout.toNanos()) {
                log.warn(
                        "ENKA rate-limit queue wait rejected uid={} estimatedWaitMs={} timeout={}",
                        maskedUid,
                        TimeUnit.NANOSECONDS.toMillis(waitNanos),
                        requestWaitTimeout
                );
                throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED);
            }
            nextRequestStartNanos = scheduled + spacingNanos;
        }

        if (waitNanos <= 0) return;
        log.info("ENKA local rate-limit wait uid={} waitMs={}", maskedUid, TimeUnit.NANOSECONDS.toMillis(waitNanos));
        try {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED, exception);
        }
    }

    private void requireBackoffExpired(String maskedUid, Instant now) {
        Instant until = globalBackoffUntil.get();
        if (!now.isBefore(until)) return;
        log.warn(
                "ENKA global 429 backoff active uid={} remainingMs={}",
                maskedUid,
                Math.max(0L, Duration.between(now, until).toMillis())
        );
        throw new AppException(ErrorCode.PROFILE_SYNC_COOLDOWN);
    }

    private Instant extendGlobalBackoff(Duration backoff) {
        Instant candidate = clock.instant().plus(backoff);
        return globalBackoffUntil.updateAndGet(current -> current.isAfter(candidate) ? current : candidate);
    }

    private Duration effective429Backoff(RestClientResponseException exception) {
        Duration effective = backoffOn429;
        HttpHeaders headers = exception.getResponseHeaders();
        if (headers == null) return effective;

        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) return effective;

        Duration serverRequested = parseRetryAfter(retryAfter.trim());
        if (serverRequested == null || serverRequested.isNegative() || serverRequested.isZero()) return effective;
        return serverRequested.compareTo(effective) > 0 ? serverRequested : effective;
    }

    private Duration parseRetryAfter(String value) {
        try {
            return Duration.ofSeconds(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Duration.between(clock.instant(), retryAt);
            } catch (RuntimeException ignoredDate) {
                return null;
            }
        }
    }

    private PublicGameProfile parse(JsonNode root, String requestedUid) {
        if (root == null) {
            log.warn("ENKA profile response was empty uid={}", maskUid(requestedUid));
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        }
        JsonNode player = root.hasNonNull("detailInfo")
                ? root.path("detailInfo")
                : root.path("player_info");
        if (player.isMissingNode() || player.isEmpty()) {
            log.warn("ENKA profile response did not contain player data uid={}", maskUid(requestedUid));
            throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
        }

        JsonNode avatars = firstArray(
                player.path("avatarDetailList"),
                player.path("avatar_list"),
                root.path("avatarDetailList"),
                root.path("characters")
        );
        List<PublicCharacter> characters = new ArrayList<>();
        avatars.forEach(node -> {
            String externalId = firstText(node, "avatarId", "avatar_id", "id");
            if (!externalId.isBlank()) {
                characters.add(new PublicCharacter(
                        externalId,
                        firstText(node, "name", "avatarName"),
                        Math.max(0, Math.min(6, firstInt(node, "rank", "eidolon")))
                ));
            }
        });

        return new PublicGameProfile(
                provider(),
                firstTextOr(player, requestedUid, "uid"),
                firstTextOr(player, "이름 없음", "nickname"),
                firstTextOr(player, "", "signature"),
                player.path("isDisplayAvatar").asBoolean(
                        player.path("is_display").asBoolean(!characters.isEmpty())
                ),
                List.copyOf(characters)
        );
    }

    private JsonNode firstArray(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate.isArray()) return candidate;
        }
        return candidates[0];
    }

    private String firstText(JsonNode node, String... fields) {
        return firstTextOr(node, "", fields);
    }

    private String firstTextOr(JsonNode node, String fallback, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asString().isBlank()) {
                return value.asString();
            }
        }
        return fallback;
    }

    private int firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.hasNonNull(field)) return node.path(field).asInt(0);
        }
        return 0;
    }

    private int admissionCapacityInUse() {
        return maxConcurrentRequests + maxWaitingRequests - admissionSlots.availablePermits();
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) return "(no message)";
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private String maskUid(String uid) {
        if (uid == null || uid.length() < 4) return "****";
        return "*****" + uid.substring(uid.length() - 4);
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) return fallback;
        return value;
    }

    private record CacheEntry(PublicGameProfile profile, Instant expiresAt) {
    }
}
