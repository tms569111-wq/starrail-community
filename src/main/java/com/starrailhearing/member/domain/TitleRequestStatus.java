package com.starrailhearing.member.domain;

public enum TitleRequestStatus {
    PENDING("검토 대기"),
    CANCELLED("취소 처리 중"),
    APPROVED("승인"),
    REJECTED("거절"),
    EXPIRED("기한 만료");

    private final String label;

    TitleRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
