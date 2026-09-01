package com.starrailhearing.moderation.domain;

import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.common.domain.BaseTimeEntity;
import com.starrailhearing.member.domain.MemberAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment_report", uniqueConstraints = @UniqueConstraint(
        name = "uq_comment_report_reporter", columnNames = {"reporter_member_id", "comment_id"}
))
public class CommentReport extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_member_id", nullable = false)
    private MemberAccount reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private CharacterComment comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(length = 500)
    private String details;

    @Column(name = "content_snapshot", nullable = false, length = 3000)
    private String contentSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_member_id")
    private MemberAccount resolvedBy;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected CommentReport() {
    }

    public CommentReport(
            MemberAccount reporter,
            CharacterComment comment,
            ReportReason reason,
            String details
    ) {
        this.reporter = reporter;
        this.comment = comment;
        this.reason = java.util.Objects.requireNonNull(reason, "신고 사유는 필수입니다.");
        this.details = normalizeOptional(details, 500);
        this.contentSnapshot = comment.getContent();
        this.status = ReportStatus.PENDING;
    }

    public void action(MemberAccount operator, String note, LocalDateTime now) {
        requirePending();
        status = ReportStatus.ACTIONED;
        resolvedBy = operator;
        resolutionNote = normalize(note, 500, "처리 메모를 입력해 주세요.");
        resolvedAt = now;
    }

    public void dismiss(MemberAccount operator, String note, LocalDateTime now) {
        requirePending();
        status = ReportStatus.DISMISSED;
        resolvedBy = operator;
        resolutionNote = normalize(note, 500, "기각 사유를 입력해 주세요.");
        resolvedAt = now;
    }

    private void requirePending() {
        if (status != ReportStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 신고입니다.");
        }
    }

    private String normalize(String value, int maximum, String emptyMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(emptyMessage);
        if (normalized.length() > maximum) return normalized.substring(0, maximum);
        return normalized;
    }

    private String normalizeOptional(String value, int maximum) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? null : normalized.substring(0, Math.min(maximum, normalized.length()));
    }

    public Long getId() { return id; }
    public MemberAccount getReporter() { return reporter; }
    public CharacterComment getComment() { return comment; }
    public ReportReason getReason() { return reason; }
    public String getDetails() { return details; }
    public String getContentSnapshot() { return contentSnapshot; }
    public ReportStatus getStatus() { return status; }
    public MemberAccount getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
