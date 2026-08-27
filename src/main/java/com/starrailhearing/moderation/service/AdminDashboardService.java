package com.starrailhearing.moderation.service;

import com.starrailhearing.comment.repository.CharacterCommentRepository;
import com.starrailhearing.common.web.PagedView;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.BadgeService;
import com.starrailhearing.member.service.BadgeView;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.moderation.domain.ReportStatus;
import com.starrailhearing.moderation.repository.CommentReportRepository;
import com.starrailhearing.moderation.repository.ModerationActionRepository;
import com.starrailhearing.vote.repository.CharacterVoteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {
    private static final int PAGE_SIZE = 20;
    private static final int MAXIMUM_PAGES = 5;

    private final MemberService memberService;
    private final BadgeService badgeService;
    private final CommentReportRepository reportRepository;
    private final ModerationActionRepository actionRepository;
    private final CharacterVoteRepository voteRepository;
    private final CharacterCommentRepository commentRepository;
    private final Clock clock;

    public AdminDashboardService(
            MemberService memberService,
            BadgeService badgeService,
            CommentReportRepository reportRepository,
            ModerationActionRepository actionRepository,
            CharacterVoteRepository voteRepository,
            CharacterCommentRepository commentRepository,
            Clock clock
    ) {
        this.memberService = memberService;
        this.badgeService = badgeService;
        this.reportRepository = reportRepository;
        this.actionRepository = actionRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.clock = clock;
    }

    public AdminDashboardView.Summary summary(long operatorId) {
        memberService.requireAdmin(operatorId);
        LocalDateTime monthStart = LocalDate.now(clock).withDayOfMonth(1).atStartOfDay();
        return new AdminDashboardView.Summary(
                memberService.count(), memberService.countActive(), memberService.countSuspended(),
                memberService.countJoinedSince(monthStart), memberService.countDeletedSince(monthStart),
                reportRepository.countByStatus(ReportStatus.PENDING),
                voteRepository.count(), commentRepository.count()
        );
    }

    public PagedView<AdminDashboardView.MemberRow> members(long operatorId, int page) {
        memberService.requireAdmin(operatorId);
        var members = memberService.recentMembers(pageRequest(page));
        Map<Long, BadgeView> badges = badgeService.findLatestForMembers(
                members.getContent().stream().map(MemberAccount::getId).toList()
        );
        return PagedView.from(members, member -> new AdminDashboardView.MemberRow(
                member.getId(), member.getNickname(), member.getEmail(), member.getRole(),
                member.getStatus(), member.isNicknameConfigured(), member.getSuspendedUntil(),
                member.getSuspensionReason(), member.getCreatedAt(), badges.get(member.getId())
        ), MAXIMUM_PAGES);
    }

    public PagedView<AdminDashboardView.ReportRow> reports(long operatorId, int page) {
        memberService.requireAdmin(operatorId);
        return PagedView.from(reportRepository.findAdminPage(pageRequest(page)),
                report -> new AdminDashboardView.ReportRow(
                        report.getId(), report.getReason(), report.getReason().getLabel(), report.getDetails(),
                        report.getStatus(), report.getReporter().getNickname(),
                        report.getComment().getMember().getId(), report.getComment().getMember().getNickname(),
                        report.getComment().getEvaluation().getCharacter().getName(),
                        report.getComment().getId(), report.getComment().getStatus(), report.getContentSnapshot(),
                        report.getResolutionNote(), report.getResolvedAt(),
                        report.getCreatedAt()
                ), MAXIMUM_PAGES);
    }

    public PagedView<AdminDashboardView.ActionRow> actions(long operatorId, int page) {
        memberService.requireAdmin(operatorId);
        return PagedView.from(actionRepository.findAllByOrderByCreatedAtDescIdDesc(pageRequest(page)),
                action -> new AdminDashboardView.ActionRow(
                        action.getId(), action.getOperator().getNickname(),
                        action.getTarget() == null
                                ? action.getTargetType() + " #" + action.getTargetId()
                                : action.getTarget().getNickname(),
                        action.getTargetType(), action.getTargetId(), action.getActionType(),
                        action.getReason(), action.getBeforeState(), action.getAfterState(), action.getCreatedAt()
                ), MAXIMUM_PAGES);
    }

    private PageRequest pageRequest(int page) {
        return PageRequest.of(Math.max(0, Math.min(page, MAXIMUM_PAGES - 1)), PAGE_SIZE);
    }
}
