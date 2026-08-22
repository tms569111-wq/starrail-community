package com.starrailhearing.moderation.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "moderation_action")
public class ModerationAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_member_id", nullable = false)
    private MemberAccount operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_member_id")
    private MemberAccount target;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ModerationActionType actionType;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "before_state", columnDefinition = "LONGTEXT")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "LONGTEXT")
    private String afterState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ModerationAction() {
    }

    public ModerationAction(
            MemberAccount operator,
            MemberAccount target,
            String targetType,
            Long targetId,
            ModerationActionType actionType,
            String reason,
            String beforeState,
            String afterState
    ) {
        this.operator = operator;
        this.target = target;
        this.targetType = normalize(targetType, 30, "TARGET");
        this.targetId = targetId;
        this.actionType = actionType;
        this.reason = normalize(reason, 500, "운영자 처리");
        this.beforeState = beforeState == null ? "{}" : beforeState;
        this.afterState = afterState == null ? "{}" : afterState;
    }

    @PrePersist
    private void beforeInsert() {
        createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    private String normalize(String value, int maximum, String fallback) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) normalized = fallback;
        return normalized.substring(0, Math.min(maximum, normalized.length()));
    }

    public Long getId() { return id; }
    public MemberAccount getOperator() { return operator; }
    public MemberAccount getTarget() { return target; }
    public String getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public ModerationActionType getActionType() { return actionType; }
    public String getReason() { return reason; }
    public String getBeforeState() { return beforeState; }
    public String getAfterState() { return afterState; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
