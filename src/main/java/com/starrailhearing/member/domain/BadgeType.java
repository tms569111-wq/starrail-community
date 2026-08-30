package com.starrailhearing.member.domain;

import java.util.Locale;

public enum BadgeType {
    BRONZE(1, "이상중재 브론즈", "#CD7F32"),
    SILVER(2, "이상중재 실버", "#C0C7D1"),
    GOLD(3, "이상중재 골드", "#FFD166"),
    PLATINUM(4, "이상중재 플래티넘", "#8DE9FF");

    private final int rank;
    private final String label;
    private final String colorHex;

    BadgeType(int rank, String label, String colorHex) {
        this.rank = rank;
        this.label = label;
        this.colorHex = colorHex;
    }

    public boolean isHigherThan(BadgeType other) {
        return other == null || rank > other.rank;
    }

    public String label() {
        return label;
    }

    public String colorHex() {
        return colorHex;
    }

    public static BadgeType from(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("칭호 등급을 확인해 주세요.", exception);
        }
    }
}
