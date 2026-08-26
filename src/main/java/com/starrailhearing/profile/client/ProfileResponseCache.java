package com.starrailhearing.profile.client;

import com.starrailhearing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class ProfileResponseCache {

    private static final Logger log = LoggerFactory.getLogger(ProfileResponseCache.class);
    private static final String KEY_PREFIX = "starrail:profile:v1:";
    private static final Duration REDIS_RETRY_DELAY = Duration.ofSeconds(30);

    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final int maximumLocalEntries;
    private final Map<String, LocalEntry> localCache;

    private volatile Instant redisRetryAt = Instant.EPOCH;

    public ProfileResponseCache(
            AppProperties properties,
            Clock clock,
            ObjectMapper objectMapper,
            Optional<StringRedisTemplate> redisTemplate
    ) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate.orElse(null);
        this.redisEnabled = properties.profileClient().redisCacheEnabled()
                && this.redisTemplate != null;
        this.maximumLocalEntries = Math.max(1, properties.profileClient().maximumCacheEntries());
        this.localCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, LocalEntry> eldest) {
                return size() > ProfileResponseCache.this.maximumLocalEntries;
            }
        };
    }

    public Optional<PublicGameProfile> get(String uid) {
        Instant now = clock.instant();
        Optional<PublicGameProfile> localValue = getFromLocal(uid, now);
        if (localValue.isPresent()) return localValue;
        if (redisEnabled && !now.isBefore(redisRetryAt)) {
            Optional<PublicGameProfile> redisValue = getFromRedis(uid, now);
            if (redisValue.isPresent()) return redisValue;
        }
        return Optional.empty();
    }

    public void put(String uid, PublicGameProfile profile, Duration ttl) {
        Duration safeTtl = positive(ttl);
        Instant now = clock.instant();
        putLocal(uid, profile, now.plus(safeTtl));
        if (!redisEnabled || now.isBefore(redisRetryAt)) return;

        try {
            redisTemplate.opsForValue().set(
                    key(uid),
                    objectMapper.writeValueAsString(profile),
                    safeTtl
            );
            redisRetryAt = Instant.EPOCH;
        } catch (Exception exception) {
            pauseRedis(exception, now);
        }
    }

    private Optional<PublicGameProfile> getFromRedis(String uid, Instant now) {
        try {
            String key = key(uid);
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                redisRetryAt = Instant.EPOCH;
                return Optional.empty();
            }
            Long remainingSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (remainingSeconds == null || remainingSeconds <= 0) {
                redisTemplate.delete(key);
                return Optional.empty();
            }
            PublicGameProfile profile = objectMapper.readValue(json, PublicGameProfile.class)
                    .withCacheTtlSeconds(remainingSeconds);
            putLocal(uid, profile, now.plusSeconds(remainingSeconds));
            redisRetryAt = Instant.EPOCH;
            return Optional.of(profile);
        } catch (Exception exception) {
            pauseRedis(exception, now);
            return Optional.empty();
        }
    }

    private Optional<PublicGameProfile> getFromLocal(String uid, Instant now) {
        synchronized (localCache) {
            LocalEntry entry = localCache.get(uid);
            if (entry == null) return Optional.empty();
            if (!now.isBefore(entry.expiresAt())) {
                localCache.remove(uid);
                return Optional.empty();
            }
            long remainingMillis = Duration.between(now, entry.expiresAt()).toMillis();
            long remainingSeconds = Math.max(1, (remainingMillis + 999) / 1000);
            return Optional.of(entry.profile().withCacheTtlSeconds(remainingSeconds));
        }
    }

    private void putLocal(String uid, PublicGameProfile profile, Instant expiresAt) {
        synchronized (localCache) {
            localCache.put(uid, new LocalEntry(profile, expiresAt));
        }
    }

    private synchronized void pauseRedis(Exception exception, Instant now) {
        if (!now.isBefore(redisRetryAt)) {
            log.warn(
                    "Redis profile cache is unavailable; using bounded local cache for {} seconds: {}",
                    REDIS_RETRY_DELAY.toSeconds(),
                    exception.getClass().getSimpleName()
            );
        }
        redisRetryAt = now.plus(REDIS_RETRY_DELAY);
    }

    private Duration positive(Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) return Duration.ofSeconds(1);
        return ttl;
    }

    private String key(String uid) {
        return KEY_PREFIX + uid;
    }

    private record LocalEntry(PublicGameProfile profile, Instant expiresAt) {
    }
}
