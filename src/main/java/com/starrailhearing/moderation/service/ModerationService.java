package com.starrailhearing.moderation.service;

import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.comment.domain.CommentStatus;
import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.member.service.SuspensionPeriod;
import com.starrailhearing.moderation.domain.CommentReport;
import com.starrailhearing.moderation.domain.ModerationAction;
import com.starrailhearing.moderation.domain.ModerationActionType;
import com.starrailhearing.moderation.domain.ReportDecision;
import com.starrailhearing.moderation.domain.ReportReason;
import com.starrailhearing.moderation.repository.CommentReportRepository;
import com.starrailhearing.moderation.repository.ModerationActionRepository;
import com.starrailhearing.profile.domain.ProfileVerificationStatus;
import com.starrailhearing.profile.repository.GameProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

@Service
@Transactional(readOnly = true)
public class ModerationService {
    private static final int DAILY_REPORT_LIMIT = 10;
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CommentReportRepository reportRepository;
    private final ModerationActionRepository actionRepository;
    private final CharacterCommentRepository commentRepository;
    private final GameProfileRepository profileRepository;
    private final MemberService memberService;
    private final Clock clock;

    public ModerationService(
            CommentReportRepository reportRepository,
            ModerationActionRepository actionRepository,
            CharacterCommentRepository commentRepository,
            GameProfileRepository profileRepository,
            MemberService memberService,
            Clock clock
    ) {
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.commentRepository = commentRepository;
        this.profileRepository = profileRepository;
        this.memberService = memberService;
        this.clock = clock;
    }

    @Transactional
    public void reportComment(
            long reporterId,
            long commentId,
            long expectedCharacterId,
            ReportReason reason,
            String details
    ) {
        MemberAccount reporter = memberService.requireActiveForWriteLocked(reporterId);
        if (!profileRepository.existsByMember_IdAndVerificationStatus(
                reporterId, ProfileVerificationStatus.VERIFIED
        )) {
            throw new AppException(ErrorCode.PROFILE_VERIFICATION_REQUIRED);
        }
        LocalDateTime today = LocalDate.now(clock.withZone(SERVICE_ZONE))
                .atStartOfDay(SERVICE_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        if (reportRepository.countByReporter_IdAndCreatedAtGreaterThanEqual(reporterId, today)
                >= DAILY_REPORT_LIMIT) {
            throw new AppException(ErrorCode.REPORT_RATE_LIMIT);
        }

        CharacterComment comment = requireComment(commentId, expectedCharacterId);
        if (comment.getMember().getId().equals(reporterId)) {
            throw new AppException(ErrorCode.SELF_REPORT_NOT_ALLOWED);
        }
        if (reportRepository.findByReporter_IdAndComment_Id(reporterId, commentId).isPresent()) {
            throw new AppException(ErrorCode.REPORT_ALREADY_EXISTS);
        }
        try {
            reportRepository.save(new CommentReport(reporter, comment, reason, details));
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.REPORT_ALREADY_EXISTS, exception);
        }
    }

    @Transactional
    public void decide(
            long operatorId,
            long reportId,
            ReportDecision decision,
            boolean hideComment,
            SuspensionPeriod sanction,
            String note
    ) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        CommentReport report = reportRepository.findForUpdate(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);

        try {
            if (decision == ReportDecision.DISMISS) {
                report.dismiss(operator, note, now);
                record(operator, report.getComment().getMember(), "REPORT", reportId,
                        ModerationActionType.DISMISS_REPORT, note, reportState("PENDING"),
                        reportState("DISMISSED"));
                return;
            }

            if (hideComment && report.getComment().getStatus() == CommentStatus.ACTIVE) {
                CharacterComment comment = report.getComment();
                String before = commentState(comment);
                comment.hideByModerator(now);
                record(operator, comment.getMember(), "COMMENT", comment.getId(),
                        ModerationActionType.HIDE_COMMENT, note, before, commentState(comment));
            }
            if (sanction != null) {
                sanctionMemberInternal(operator, report.getComment().getMember(), sanction, note);
            }
            report.action(operator, note, now);
            record(operator, report.getComment().getMember(), "REPORT", reportId,
                    ModerationActionType.ACTION_REPORT, note, reportState("PENDING"),
                    reportState("ACTIONED"));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
    }

    @Transactional
    public void sanctionMember(
            long operatorId,
            long targetId,
            SuspensionPeriod period,
            String reason
    ) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        MemberAccount target = memberService.require(targetId);
        sanctionMemberInternal(operator, target, period, reason);
    }

    @Transactional
    public void restoreMember(long operatorId, long targetId, String reason) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        MemberAccount target = memberService.require(targetId);
        String before = memberState(target);
        memberService.restoreByAdmin(operatorId, targetId);
        record(operator, target, "MEMBER", targetId, ModerationActionType.RESTORE_WRITE,
                reason, before, memberState(target));
    }

    @Transactional
    public void restoreComment(long operatorId, long commentId, String reason) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        CharacterComment comment = commentRepository.findDetailedById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        String before = commentState(comment);
        try {
            comment.restoreByModerator();
        } catch (IllegalStateException exception) {
            throw new AppException(ErrorCode.INVALID_INPUT, exception.getMessage());
        }
        record(operator, comment.getMember(), "COMMENT", commentId,
                ModerationActionType.RESTORE_COMMENT, reason, before, commentState(comment));
    }

    @Transactional
    public void recordBadgeAction(
            long operatorId,
            long targetId,
            ModerationActionType actionType,
            String reason
    ) {
        MemberAccount operator = memberService.requireAdmin(operatorId);
        MemberAccount target = memberService.require(targetId);
        record(operator, target, "MEMBER_BADGE", targetId, actionType, reason, "{}", "{}");
    }

    private CharacterComment requireComment(long commentId, long expectedCharacterId) {
        CharacterComment comment = commentRepository.findByIdAndStatus(commentId, CommentStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.getEvaluation().getCharacter().getId().equals(expectedCharacterId)) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return comment;
    }

    private void sanctionMemberInternal(
            MemberAccount operator,
            MemberAccount target,
            SuspensionPeriod period,
            String reason
    ) {
        String before = memberState(target);
        memberService.suspendByAdmin(operator.getId(), target.getId(), period, reason);
        record(operator, target, "MEMBER", target.getId(), actionType(period), reason,
                before, memberState(target));
    }

    private ModerationActionType actionType(SuspensionPeriod period) {
        return switch (period) {
            case WARNING -> ModerationActionType.WARNING;
            case DAY_1 -> ModerationActionType.SUSPEND_1_DAY;
            case DAYS_7 -> ModerationActionType.SUSPEND_7_DAYS;
            case DAYS_30 -> ModerationActionType.SUSPEND_30_DAYS;
            case PERMANENT -> ModerationActionType.SUSPEND_PERMANENT;
        };
    }

    private void record(
            MemberAccount operator,
            MemberAccount target,
            String targetType,
            Long targetId,
            ModerationActionType type,
            String reason,
            String beforeState,
            String afterState
    ) {
        actionRepository.save(new ModerationAction(
                operator, target, targetType, targetId, type, reason, beforeState, afterState
        ));
    }

    private String memberState(MemberAccount member) {
        return "{\"status\":\"" + member.getStatus() + "\",\"suspendedUntil\":"
                + quote(member.getSuspendedUntil()) + "}";
    }

    private String commentState(CharacterComment comment) {
        return "{\"status\":\"" + comment.getStatus() + "\"}";
    }

    private String reportState(String status) {
        return "{\"status\":\"" + status + "\"}";
    }

    private String quote(Object value) {
        return value == null ? "null" : "\"" + value + "\"";
    }
}
