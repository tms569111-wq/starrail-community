package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class EnkaGameProfileClient implements ProfileProviderClient {

    private static final Logger log = LoggerFactory.getLogger(EnkaGameProfileClient.class);
    private static final String CACHE_PREFIX = "enka:hsr:uid:";
    private static final String LOCK_PREFIX = "enka:hsr:lock:";
    private static final String RATE_PREFIX = "enka:hsr:rate:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(2);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final RestClient restClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int maxRequestsPerSecond;
    private final AtomicLong localRateSecond = new AtomicLong(-1);
    private final AtomicInteger localRateCount = new AtomicInteger();

    public EnkaGameProfileClient(
            @Qualifier("enkaRestClient") RestClient enkaRestClient,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.enka.max-requests-per-second:3}") int maxRequestsPerSecond
    ) {
        this.restClient = enkaRestClient;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxRequestsPerSecond = Math.max(1, maxRequestsPerSecond);
    }

    @Override
    public ProfileProvider provider() {
        return ProfileProvider.ENKA;
    }

    @Override
    public PublicGameProfile fetch(String uid, boolean forceUpdate) {
        // Enka 자체 캐시는 ttl이 끝날 때까지 같은 응답을 반환한다.
        // forceUpdate=true여도 Redis에 살아 있는 Enka 응답은 재요청하지 않는다.
        JsonNode cached = readCached(uid);
        if (cached != null) return parse(cached, uid);

        String lockToken = acquireLock(uid);
        if (lockToken == null) {
            JsonNode filledByOtherRequest = waitForCache(uid);
            if (filledByOtherRequest != null) return parse(filledByOtherRequest, uid);
            throw new AppException(
                    ErrorCode.PROFILE_SYNC_COOLDOWN,
                    "같은 UID를 조회 중입니다. 잠시 후 다시 시도해 주세요."
            );
        }

        try {
            JsonNode filledWhileLocking = readCached(uid);
            if (filledWhileLocking != null) return parse(filledWhileLocking, uid);
            requireRequestBudget();

            JsonNode root = request(uid);
            cacheUsingEnkaTtl(uid, root);
            return parse(root, uid);
        } finally {
            releaseLock(uid, lockToken);
        }
    }

    private JsonNode request(String uid) {
        try {
            JsonNode root = restClient.get()
                    .uri("/api/hsr/uid/{uid}", uid)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                log.warn("Enka profile response was empty");
                throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED);
            }
            return root;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            log.warn("Enka profile request failed with HTTP {}", status.value());
            if (status.value() == 429) {
                throw new AppException(ErrorCode.PROFILE_SYNC_COOLDOWN, exception);
            }
            if (status.value() == 400 || status.value() == 404 || status.value() == 422) {
                throw new AppException(ErrorCode.PROFILE_LOOKUP_FAILED, exception);
            }
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        } catch (RestClientException exception) {
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        }
    }

    private JsonNode readCached(String uid) {
        try {
            String json = redis.opsForValue().get(CACHE_PREFIX + uid);
            if (json == null || json.isBlank()) return null;
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            log.warn("Redis Enka cache read failed; falling back to upstream", exception);
            return null;
        }
    }

    private void cacheUsingEnkaTtl(String uid, JsonNode root) {
        long ttlSeconds = Math.max(1, root.path("ttl").asLong(1));
        try {
            redis.opsForValue().set(
                    CACHE_PREFIX + uid,
                    root.toString(),
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (RuntimeException exception) {
            log.warn("Redis Enka cache write failed; response will not be shared", exception);
        }
    }

    private String acquireLock(String uid) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    LOCK_PREFIX + uid,
                    token,
                    LOCK_TTL
            );
            return Boolean.TRUE.equals(acquired) ? token : null;
        } catch (RuntimeException exception) {
            // Redis 장애가 프로필 기능 전체 장애로 번지지는 않게 한다.
            log.warn("Redis Enka lock failed; continuing without distributed lock", exception);
            return "redis-unavailable-" + token;
        }
    }

    private void releaseLock(String uid, String token) {
        if (token.startsWith("redis-unavailable-")) return;
        try {
            redis.execute(RELEASE_LOCK, List.of(LOCK_PREFIX + uid), token);
        } catch (RuntimeException exception) {
            log.warn("Redis Enka lock release failed", exception);
        }
    }

    private JsonNode waitForCache(String uid) {
        long deadline = System.nanoTime() + LOCK_WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(75);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
            JsonNode cached = readCached(uid);
            if (cached != null) return cached;
        }
        return null;
    }

    private void requireRequestBudget() {
        long second = Instant.now().getEpochSecond();
        String key = RATE_PREFIX + second;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, Duration.ofSeconds(2));
            if (count != null && count > maxRequestsPerSecond) {
                throw new AppException(
                        ErrorCode.PROFILE_SYNC_COOLDOWN,
                        "프로필 조회가 몰리고 있습니다. 잠시 후 다시 시도해 주세요."
                );
            }
            return;
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Redis Enka rate limiter failed; using local limiter", exception);
        }

        long previous = localRateSecond.getAndSet(second);
        if (previous != second) localRateCount.set(0);
        if (localRateCount.incrementAndGet() > maxRequestsPerSecond) {
            throw new AppException(
                    ErrorCode.PROFILE_SYNC_COOLDOWN,
                    "프로필 조회가 몰리고 있습니다. 잠시 후 다시 시도해 주세요."
            );
        }
    }

    private PublicGameProfile parse(JsonNode root, String requestedUid) {
        JsonNode player = root.hasNonNull("detailInfo")
                ? root.path("detailInfo")
                : root.path("player_info");
        if (player.isMissingNode() || player.isEmpty()) {
            log.warn("Enka profile response did not contain player data");
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
}
