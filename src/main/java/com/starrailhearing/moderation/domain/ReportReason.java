package com.starrailhearing.moderation.domain;

public enum ReportReason {
    SPAM("도배·광고"),
    ABUSE("욕설·괴롭힘"),
    PRIVACY("개인정보 노출"),
    SPOILER("스포일러"),
    FALSE_INFORMATION("허위 정보"),
    IMPERSONATION("사칭"),
    OTHER("기타");

    private final String label;

    ReportReason(String label) { this.label = label; }
    public String getLabel() { return label; }

    public static ReportReason from(String value) {
        try {
            return valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return OTHER;
        }
    }
}
