package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

@Component
public class ProfileProviderRequestPacer {

    private static final Logger log = LoggerFactory.getLogger(ProfileProviderRequestPacer.class);
    private static final String KEY_PREFIX = "starrail:profile:provider-rate:v1:";
    private static final long REDIS_RETRY_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final long intervalNanos;
    private final long maximumWaitNanos;
    private final Map<ProfileProvider, AtomicLong> localNextStart = new EnumMap<>(ProfileProvider.class);

    private volatile long redisRetryAtNanos;

    public ProfileProviderRequestPacer(
            AppProperties properties,
            Optional<StringRedisTemplate> redisTemplate
    ) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.redisEnabled = properties.profileClient().redisCacheEnabled()
                && this.redisTemplate != null;
        this.intervalNanos = nonNegativeNanos(
                properties.profileClient().providerMinimumInterval()
        );
        this.maximumWaitNanos = nonNegativeNanos(
                properties.profileClient().providerRateWait()
        );
        for (ProfileProvider provider : ProfileProvider.values()) {
            localNextStart.put(provider, new AtomicLong(Long.MIN_VALUE));
        }
    }

    public void await(ProfileProvider provider) {
        if (intervalNanos == 0) return;
        long deadline = saturatingAdd(System.nanoTime(), maximumWaitNanos);
        if (redisEnabled && System.nanoTime() >= redisRetryAtNanos) {
            if (awaitRedis(provider, deadline)) return;
        }
        awaitLocal(provider, deadline);
    }

    private boolean awaitRedis(ProfileProvider provider, long deadline) {
        Duration interval = Duration.ofNanos(intervalNanos);
        while (true) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                        KEY_PREFIX + provider.name(),
                        "1",
                        interval
                );
                if (Boolean.TRUE.equals(acquired)) {
                    redisRetryAtNanos = 0;
                    return true;
                }
            } catch (RuntimeException exception) {
                pauseRedis(exception);
                return false;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw busy();
            pause(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(50)));
        }
    }

    private void awaitLocal(ProfileProvider provider, long deadline) {
        AtomicLong nextStart = localNextStart.get(provider);
        while (true) {
            long now = System.nanoTime();
            long current = nextStart.get();
            long slot = Math.max(now, current);
            long delay = Math.max(0, slot - now);
            if (delay > Math.max(0, deadline - now)) throw busy();
            if (!nextStart.compareAndSet(current, saturatingAdd(slot, intervalNanos))) continue;
            pause(delay);
            return;
        }
    }

    private synchronized void pauseRedis(RuntimeException exception) {
        long now = System.nanoTime();
        if (now >= redisRetryAtNanos) {
            log.warn(
                    "Redis provider rate gate is unavailable; using local pacing for 30 seconds: {}",
                    exception.getClass().getSimpleName()
            );
        }
        redisRetryAtNanos = saturatingAdd(now, REDIS_RETRY_NANOS);
    }

    private void pause(long nanos) {
        if (nanos <= 0) return;
        LockSupport.parkNanos(nanos);
        if (Thread.currentThread().isInterrupted()) {
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, "UID 조회 대기가 중단되었습니다.");
        }
    }

    private AppException busy() {
        return new AppException(
                ErrorCode.UPSTREAM_UNAVAILABLE,
                "현재 UID 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요."
        );
    }

    private long nonNegativeNanos(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) return 0;
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
