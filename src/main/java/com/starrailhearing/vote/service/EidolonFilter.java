package com.starrailhearing.vote.service;

public enum EidolonFilter {
    ALL("전체", null, null),
    E0("0돌", 0, 0),
    E1("1돌", 1, 1),
    E2("2돌", 2, 2),
    E3_TO_E5("3~5돌", 3, 5),
    E6("풀돌", 6, 6);

    private final String label;
    private final Integer minimum;
    private final Integer maximum;

    EidolonFilter(String label, Integer minimum, Integer maximum) {
        this.label = label;
        this.minimum = minimum;
        this.maximum = maximum;
    }

    public String getLabel() {
        return label;
    }

    public Integer getMinimum() {
        return minimum;
    }

    public Integer getMaximum() {
        return maximum;
    }

    public static EidolonFilter from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }

    public static EidolonFilter forEidolon(int eidolon) {
        return switch (eidolon) {
            case 0 -> E0;
            case 1 -> E1;
            case 2 -> E2;
            case 3, 4, 5 -> E3_TO_E5;
            case 6 -> E6;
            default -> throw new IllegalArgumentException("성혼은 0~6 사이여야 합니다.");
        };
    }
}
