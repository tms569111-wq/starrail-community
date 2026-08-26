package com.starrailhearing.profile.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ProfileFetchBulkhead {
    private final Semaphore permits;
    private final Duration wait;

    public ProfileFetchBulkhead(AppProperties properties) {
        int maximum = Math.max(1, properties.profileClient().maximumConcurrentRequests());
        this.permits = new Semaphore(maximum, true);
        this.wait = properties.profileClient().bulkheadWait();
    }

    public <T> T execute(Supplier<T> action) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(
                    Math.max(0, wait.toMillis()),
                    TimeUnit.MILLISECONDS
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, exception);
        }
        if (!acquired) {
            throw new AppException(
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "현재 UID 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요."
            );
        }
        try {
            return action.get();
        } finally {
            permits.release();
        }
    }
}
