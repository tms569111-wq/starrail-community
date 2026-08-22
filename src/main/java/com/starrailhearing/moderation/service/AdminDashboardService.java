package com.starrailhearing.moderation.service;

import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.member.service.BadgeView;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.moderation.domain.ReportStatus;
import com.starrailhearing.moderation.repository.CommentReportRepository;
import com.starrailhearing.moderation.repository.ModerationActionRepository;
import com.starrailhearing.vote.repository.CharacterVoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {
    private final MemberService memberService;
    private final BadgeService badgeService;
    private final CommentReportRepository reportRepository;
    private final ModerationActionRepository actionRepository;
    private final CharacterVoteRepository voteRepository;
    private final CharacterCommentRepository commentRepository;
    private final AppProperties properties;
    private final Clock clock;

    public AdminDashboardService(
            MemberService memberService,
            BadgeService badgeService,
            CommentReportRepository reportRepository,
            ModerationActionRepository actionRepository,
            CharacterVoteRepository voteRepository,
            CharacterCommentRepository commentRepository,
            AppProperties properties,
            Clock clock
    ) {
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public AdminDashboardView view(long operatorId) {
        memberService.requireAdmin(operatorId);
        List<MemberAccount> members = memberService.recentMembers();
        Map<Long, BadgeView> badges = badgeService.findForMembers(
                members.stream().map(MemberAccount::getId).toList(),
                properties.operator().platinumVersion()
        );
        LocalDateTime monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();

        var memberRows = members.stream().map(member -> new AdminDashboardView.MemberRow(
                member.getId(), member.getNickname(), member.getEmail(), member.getRole(),
                member.getStatus(), member.isNicknameConfigured(), member.getSuspendedUntil(),
                member.getSuspensionReason(), member.getCreatedAt(), badges.get(member.getId())
        )).toList();

        var reportRows = reportRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(report -> new AdminDashboardView.ReportRow(
                        report.getId(), report.getReason(), report.getReason().getLabel(), report.getDetails(),
                        report.getStatus(), report.getReporter().getNickname(),
                        report.getComment().getMember().getId(), report.getComment().getMember().getNickname(),
                        report.getComment().getEvaluation().getCharacter().getName(),
                        report.getComment().getId(), report.getComment().getStatus(), report.getContentSnapshot(),
                        report.getCreatedAt()
                )).toList();

        var actionRows = actionRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(action -> new AdminDashboardView.ActionRow(
                        action.getId(), action.getOperator().getNickname(),
                        action.getTarget() == null
                                ? action.getTargetType() + " #" + action.getTargetId()
                                : action.getTarget().getNickname(),
                        action.getTargetType(), action.getTargetId(), action.getActionType(),
                        action.getReason(), action.getBeforeState(), action.getAfterState(), action.getCreatedAt()
                )).toList();

        return new AdminDashboardView(
                new AdminDashboardView.Summary(
                        memberService.count(), memberService.countActive(), memberService.countSuspended(),
                        memberService.countJoinedSince(monthStart), memberService.countDeletedSince(monthStart),
                        reportRepository.countByStatus(ReportStatus.PENDING),
                        voteRepository.count(), commentRepository.count()
                ),
                memberRows, reportRows, actionRows
        );
    }
}
