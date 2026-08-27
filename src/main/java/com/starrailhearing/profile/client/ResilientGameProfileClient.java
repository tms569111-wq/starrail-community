package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ResilientGameProfileClient implements GameProfileClient {

    private static final Logger log = LoggerFactory.getLogger(ResilientGameProfileClient.class);

    private final List<ProfileProviderClient> providers;
    private final AppProperties.ProfileClient properties;
    private final Clock clock;
    private final int cacheMaxEntries;
    private final Duration requestWaitTimeout;
    private final Semaphore admissionSlots;
    private final Semaphore concurrentSlots;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, CompletableFuture<PublicGameProfile>> inFlight = new ConcurrentHashMap<>();
    private final Map<ProfileProvider, CircuitState> circuits = new EnumMap<>(ProfileProvider.class);

    public ResilientGameProfileClient(
            List<ProfileProviderClient> providers,
            AppProperties properties,
            Clock clock
    ) {
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(client -> client.provider() == ProfileProvider.ENKA ? 0 : 1))
                .toList();
        this.properties = properties.profileClient();
        this.clock = clock;

        AppProperties.Enka traffic = properties.enka();
        this.cacheMaxEntries = Math.max(1, traffic.cacheMaxEntries());
        int maxConcurrentRequests = Math.max(1, traffic.maxConcurrentRequests());
        int maxWaitingRequests = Math.max(0, traffic.maxWaitingRequests());
        this.requestWaitTimeout = positiveDuration(traffic.requestWaitTimeout(), Duration.ofSeconds(15));
        long totalCapacity = (long) maxConcurrentRequests + maxWaitingRequests;
        int admissionCapacity = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, totalCapacity));
        this.admissionSlots = new Semaphore(admissionCapacity, true);
        this.concurrentSlots = new Semaphore(maxConcurrentRequests, true);

        for (ProfileProviderClient provider : providers) {
            circuits.put(provider.provider(), new CircuitState());
        }
        log.info(
                "Profile provider order={} trafficGuardConcurrent={} trafficGuardWaiting={} cacheMaxEntries={}",
                this.providers.stream().map(ProfileProviderClient::provider).toList(),
                maxConcurrentRequests,
                maxWaitingRequests,
                cacheMaxEntries
        );
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        Instant now = clock.instant();
        String maskedUid = maskUid(uid);
        CacheEntry cached = readCacheEntry(uid, now);
        if (!forceUpdate && cached != null && now.isBefore(cached.expiresAt())) {
            log.info("PROFILE shared JVM cache hit uid={} provider={}", maskedUid, cached.profile().provider());
            return cached.profile();
        }

        CompletableFuture<PublicGameProfile> owned = new CompletableFuture<>();
        CompletableFuture<PublicGameProfile> existing = inFlight.putIfAbsent(uid, owned);
        if (existing != null) {
            log.info("PROFILE joined in-flight UID request uid={}", maskedUid);
            return awaitInFlight(existing, maskedUid);
        }

        boolean admissionAcquired = false;
        boolean concurrentAcquired = false;
        try {
            admissionAcquired = admissionSlots.tryAcquire();
            if (!admissionAcquired) {
                log.warn("PROFILE provider queue full uid={}", maskedUid);
                throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED);
            }

            concurrentAcquired = acquireConcurrentSlot(maskedUid);
            PublicGameProfile profile = fetchFromProviders(uid, forceUpdate, cached, maskedUid);
            owned.complete(profile);
            return profile;
        } catch (RuntimeException exception) {
            owned.completeExceptionally(exception);
            throw exception;
        } catch (Error error) {
            owned.completeExceptionally(error);
            throw error;
        } finally {
            if (concurrentAcquired) concurrentSlots.release();
            if (admissionAcquired) admissionSlots.release();
            inFlight.remove(uid, owned);
        }
    }

    private PublicGameProfile fetchFromProviders(
            String uid,
            boolean forceUpdate,
            CacheEntry cached,
            String maskedUid
    ) {

        AppException last = null;
        AppException lookupFailure = null;
        for (ProfileProviderClient provider : providers) {
            Instant now = clock.instant();
            CircuitState circuit = circuits.get(provider.provider());
            if (circuit.isOpen(now)) {
                log.warn(
                        "PROFILE provider skipped because circuit is open provider={} uid={} retryAt={}",
                        provider.provider(),
                        maskedUid,
                        circuit.openUntil
                );
                continue;
            }

            log.info("PROFILE provider attempt provider={} uid={} forceUpdate={}", provider.provider(), maskedUid, forceUpdate);
            try {
                PublicGameProfile profile = provider.fetch(uid, forceUpdate);
                circuit.success();
                putCacheEntry(uid, new CacheEntry(profile, clock.instant().plus(properties.cacheTtl())));
                log.info(
                        "PROFILE provider success provider={} uid={} characters={}",
                        provider.provider(),
                        maskedUid,
                        profile.characters().size()
                );
                return profile;
            } catch (AppException exception) {
                last = exception;
                ErrorCode errorCode = exception.getErrorCode();
                log.warn(
                        "PROFILE provider failure provider={} uid={} errorCode={}",
                        provider.provider(),
                        maskedUid,
                        errorCode
                );

                if (errorCode == ErrorCode.UPSTREAM_UNAVAILABLE
                        || errorCode == ErrorCode.PROFILE_UPSTREAM_THROTTLED) {
                    circuit.failure(clock.instant(), properties.circuitFailureThreshold(),
                            properties.circuitOpenDuration());
                    log.warn(
                            "PROFILE fallback after provider failure provider={} uid={} failures={} circuitOpenUntil={}",
                            provider.provider(),
                            maskedUid,
                            circuit.failures,
                            circuit.openUntil
                    );
                    continue;
                }
                if (errorCode == ErrorCode.PROFILE_LOOKUP_FAILED) {
                    lookupFailure = exception;
                    log.info("PROFILE lookup failed on provider={}, trying next provider uid={}", provider.provider(), maskedUid);
                    continue;
                }
                if (errorCode == ErrorCode.PROFILE_NOT_PUBLIC) {
                    log.info("PROFILE display data missing on provider={}, trying next provider uid={}", provider.provider(), maskedUid);
                    continue;
                }

                log.warn("PROFILE request stopped without fallback uid={} errorCode={}", maskedUid, errorCode);
                throw exception;
            }
        }

        if (!forceUpdate && cached != null) {
            log.warn("PROFILE all providers failed; serving stale JVM cache uid={} provider={}", maskedUid, cached.profile().provider());
            return cached.profile();
        }
        if (lookupFailure != null) {
            log.warn("PROFILE all providers exhausted with lookup failure uid={}", maskedUid);
            throw lookupFailure;
        }
        ErrorCode finalCode = last == null ? ErrorCode.UPSTREAM_UNAVAILABLE : last.getErrorCode();
        log.error("PROFILE all providers failed uid={} finalErrorCode={}", maskedUid, finalCode);
        throw last == null ? new AppException(ErrorCode.UPSTREAM_UNAVAILABLE) : last;
    }

    private boolean acquireConcurrentSlot(String maskedUid) {
        try {
            boolean acquired = concurrentSlots.tryAcquire(requestWaitTimeout.toNanos(), TimeUnit.NANOSECONDS);
            if (!acquired) {
                log.warn("PROFILE provider-slot wait timed out uid={} timeout={}", maskedUid, requestWaitTimeout);
                throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED);
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED, exception);
        }
    }

    private PublicGameProfile awaitInFlight(
            CompletableFuture<PublicGameProfile> future,
            String maskedUid
    ) {
        try {
            return future.get(requestWaitTimeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED, exception);
        } catch (TimeoutException exception) {
            log.warn("PROFILE in-flight UID wait timed out uid={} timeout={}", maskedUid, requestWaitTimeout);
            throw new AppException(ErrorCode.PROFILE_REQUEST_THROTTLED, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof AppException appException) throw appException;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, cause);
        }
    }

    private CacheEntry readCacheEntry(String uid, Instant now) {
        synchronized (cache) {
            CacheEntry cached = cache.get(uid);
            if (cached != null && !now.isBefore(cached.expiresAt())) {
                cache.remove(uid);
            }
            return cached;
        }
    }

    private void putCacheEntry(String uid, CacheEntry entry) {
        synchronized (cache) {
            cache.put(uid, entry);
            while (cache.size() > cacheMaxEntries) {
                String eldestUid = cache.keySet().iterator().next();
                cache.remove(eldestUid);
            }
        }
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) return fallback;
        return value;
    }

    public List<ProfileProviderHealthView> providerStatuses() {
        Instant now = clock.instant();
        ZoneId zone = clock.getZone();
        return providers.stream().map(provider -> {
            CircuitState state = circuits.get(provider.provider());
            Instant retry = state.openUntil;
            return new ProfileProviderHealthView(
                    provider.provider(),
                    state.isOpen(now),
                    state.failures,
                    retry == null ? null : LocalDateTime.ofInstant(retry, zone)
            );
        }).toList();
    }

    private String maskUid(String uid) {
        if (uid == null || uid.length() < 4) return "****";
        return "*****" + uid.substring(uid.length() - 4);
    }

    private record CacheEntry(PublicGameProfile profile, Instant expiresAt) {
    }

    private static final class CircuitState {
        private volatile int failures;
        private volatile Instant openUntil;

        synchronized boolean isOpen(Instant now) {
            if (openUntil != null && !now.isBefore(openUntil)) {
                failures = 0;
                openUntil = null;
            }
            return openUntil != null;
        }

        synchronized void success() {
            failures = 0;
            openUntil = null;
        }

        synchronized void failure(Instant now, int threshold, java.time.Duration openDuration) {
            failures++;
            if (failures >= Math.max(1, threshold)) openUntil = now.plus(openDuration);
        }
    }
}
