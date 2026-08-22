package com.starrailhearing.moderation.domain;

import com.starrailhearing.comment.domain.CharacterComment;
import com.starrailhearing.member.domain.MemberAccount;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentReportTest {

    @Test
    void 대기_신고를_운영자가_처리한다() {
        MemberAccount reporter = MemberAccount.google("reporter", "reporter@example.com", "신고자");
        MemberAccount operator = MemberAccount.google("operator", "operator@example.com", "별빛 개척자");
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 8, 15, 12, 30);
        CharacterComment comment = mock(CharacterComment.class);
        when(comment.getContent()).thenReturn("접수 당시 광고 내용");
        CommentReport report = new CommentReport(reporter, comment, ReportReason.SPAM, null);

        report.action(operator, "확인 후 숨김", resolvedAt);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.ACTIONED);
        assertThat(report.getContentSnapshot()).isEqualTo("접수 당시 광고 내용");
        assertThat(report.getDetails()).isNull();
        assertThat(report.getResolvedBy()).isSameAs(operator);
        assertThat(report.getResolvedAt()).isEqualTo(resolvedAt);
    }

    @Test
    void 이미_처리된_신고는_다시_처리할_수_없다() {
        MemberAccount reporter = MemberAccount.google("reporter", "reporter@example.com", "신고자");
        MemberAccount operator = MemberAccount.google("operator", "operator@example.com", "별빛 개척자");
        CharacterComment comment = mock(CharacterComment.class);
        when(comment.getContent()).thenReturn("댓글");
        CommentReport report = new CommentReport(reporter, comment, ReportReason.OTHER, "검토 요청");
        report.dismiss(operator, "문제 없음", LocalDateTime.now());

        assertThatThrownBy(() -> report.action(operator, "재처리", LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }
}
