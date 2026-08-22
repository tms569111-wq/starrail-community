package com.starrailhearing.moderation.domain;

public enum ReportDecision {
    DISMISS,
    ACTION;

    public static ReportDecision from(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("알 수 없는 신고 처리 방식입니다.");
        }
    }
}
