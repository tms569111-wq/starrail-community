package com.starrailhearing.moderation.service;

import com.starrailhearing.member.domain.MemberRole;
import com.starrailhearing.member.domain.MemberStatus;
import com.starrailhearing.member.service.BadgeView;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.domain.ReportReason;
import com.starrailhearing.moderation.domain.ReportStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardView(
        Summary summary,
        List<MemberRow> members,
        List<ReportRow> reports,
        List<ActionRow> actions
) {
    public record Summary(
            long totalMembers,
            long activeMembers,
            long suspendedMembers,
            long joinedThisMonth,
            long deletedThisMonth,
            long pendingReports,
            long votes,
            long comments
    ) {
    }

    public record MemberRow(
            long id,
            String nickname,
            String email,
            MemberRole role,
            MemberStatus status,
            boolean nicknameConfigured,
            LocalDateTime suspendedUntil,
            String suspensionReason,
            LocalDateTime joinedAt,
            BadgeView badge
    ) {
    }

    public record ReportRow(
            long id,
            ReportReason reason,
            String reasonLabel,
            String details,
            ReportStatus status,
            String reporterNickname,
            long authorId,
            String authorNickname,
            String characterName,
            long commentId,
            com.starrailhearing.comment.domain.CommentStatus commentStatus,
            String contentSnapshot,
            LocalDateTime createdAt
    ) {
    }

    public record ActionRow(
            long id,
            String operatorNickname,
            String targetDisplay,
            String targetType,
            Long targetId,
            ModerationActionType actionType,
            String reason,
            String beforeState,
            String afterState,
            LocalDateTime createdAt
    ) {
    }
}
