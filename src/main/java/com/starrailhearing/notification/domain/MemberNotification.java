package com.starrailhearing.notification.domain;

import com.starrailhearing.member.domain.MemberAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "member_notification")
public class MemberNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    protected MemberNotification() {
    }

    public MemberNotification(MemberAccount member, String title, String message) {
        this.member = java.util.Objects.requireNonNull(member, "알림 대상은 필수입니다.");
        this.title = normalize(title, 120, "운영 알림");
        this.message = normalize(message, 1000, "운영 처리 결과를 확인해 주세요.");
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    private String normalize(String value, int maximum, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) normalized = fallback;
        return normalized.substring(0, Math.min(maximum, normalized.length()));
    }

    public Long getId() { return id; }
    public MemberAccount getMember() { return member; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getReadAt() { return readAt; }
}
