package com.starrailhearing.member.service;

import java.time.Duration;

public enum SuspensionPeriod {
    WARNING(null),
    DAY_1(Duration.ofDays(1)),
    DAYS_7(Duration.ofDays(7)),
    DAYS_30(Duration.ofDays(30)),
    PERMANENT(null);

    private final Duration duration;

    SuspensionPeriod(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() { return duration; }

    public static SuspensionPeriod from(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("알 수 없는 제재 기간입니다.");
        }
    }
}
