package com.starrailhearing.member.service;

import java.time.Duration;

public enum SuspensionPeriod {
    WARNING(null, "경고"),
    DAY_1(Duration.ofDays(1), "1일 작성 정지"),
    DAYS_7(Duration.ofDays(7), "7일 작성 정지"),
    DAYS_30(Duration.ofDays(30), "30일 작성 정지"),
    PERMANENT(null, "영구 작성 정지");

    private final Duration duration;
    private final String label;

    SuspensionPeriod(Duration duration, String label) {
        this.duration = duration;
        this.label = label;
    }

    public Duration duration() { return duration; }
    public String getLabel() { return label; }

    public static SuspensionPeriod from(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("알 수 없는 제재 기간입니다.");
        }
    }
}
