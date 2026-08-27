package com.starrailhearing.member.domain;

import com.starrailhearing.common.domain.BaseTimeEntity;
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

import java.time.LocalDateTime;

@Entity
@Table(name = "title_verification_request")
public class TitleVerificationRequest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private MemberAccount member;

    @Column(name = "game_version", nullable = false, length = 20)
    private String gameVersion;

    @Column(name = "private_image_path", length = 500)
    private String privateImagePath;

    @Column(name = "image_mime_type", length = 50)
    private String imageMimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TitleRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_member_id")
    private MemberAccount reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    protected TitleVerificationRequest() {
    }

    public TitleVerificationRequest(
            MemberAccount member,
            String gameVersion,
            String privateImagePath,
            String imageMimeType,
            LocalDateTime expiresAt
    ) {
        this.member = member;
        this.gameVersion = requireText(gameVersion, 20, "게임 버전");
        this.privateImagePath = requireText(privateImagePath, 500, "이미지 경로");
        this.imageMimeType = requireText(imageMimeType, 50, "이미지 형식");
        this.status = TitleRequestStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    public String approve(MemberAccount operator, String note, LocalDateTime now) {
        return decide(operator, note, now, TitleRequestStatus.APPROVED);
    }

    public String reject(MemberAccount operator, String note, LocalDateTime now) {
        return decide(operator, note, now, TitleRequestStatus.REJECTED);
    }

    public String cancel(long memberId, LocalDateTime now) {
        requirePending();
        if (!member.getId().equals(memberId)) {
            throw new IllegalStateException("본인의 칭호 신청만 취소할 수 있습니다.");
        }
        String path = privateImagePath;
        status = TitleRequestStatus.CANCELED;
        reviewNote = "신청자 취소";
        reviewedAt = now;
        return path;
    }

    public String expire(LocalDateTime now) {
        requirePending();
        String path = privateImagePath;
        status = TitleRequestStatus.EXPIRED;
        reviewNote = "검토 기한 만료";
        reviewedAt = now;
        return path;
    }

    private String decide(
            MemberAccount operator,
            String note,
            LocalDateTime now,
            TitleRequestStatus decision
    ) {
        requirePending();
        if (!now.isBefore(expiresAt)) throw new IllegalStateException("검토 기한이 지난 신청입니다.");
        String path = privateImagePath;
        status = decision;
        reviewedBy = operator;
        reviewNote = requireText(note, 500, "검토 메모");
        reviewedAt = now;
        return path;
    }

    public void clearEvidence(String expectedPath) {
        if (privateImagePath != null && privateImagePath.equals(expectedPath)) {
            privateImagePath = null;
            imageMimeType = null;
        }
    }

    private void requirePending() {
        if (status != TitleRequestStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 칭호 신청입니다.");
        }
    }

    private String requireText(String value, int maximum, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maximum) {
            throw new IllegalArgumentException(field + " 값을 확인해 주세요.");
        }
        return normalized;
    }

    public Long getId() { return id; }
    public MemberAccount getMember() { return member; }
    public String getGameVersion() { return gameVersion; }
    public String getPrivateImagePath() { return privateImagePath; }
    public String getImageMimeType() { return imageMimeType; }
    public TitleRequestStatus getStatus() { return status; }
    public MemberAccount getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
}
