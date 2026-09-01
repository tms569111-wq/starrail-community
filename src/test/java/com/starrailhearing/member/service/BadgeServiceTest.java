package com.starrailhearing.member.service;

import com.starrailhearing.member.domain.BadgeType;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.domain.MemberBadge;
import com.starrailhearing.member.repository.MemberBadgeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void 같은_버전의_칭호는_새_행_대신_상위_등급으로_갱신한다() {
        MemberBadgeRepository repository = mock(MemberBadgeRepository.class);
        MemberService memberService = mock(MemberService.class);
        MemberAccount member = MemberAccount.google("user", "user@example.com", "유저");
        MemberAccount operator = MemberAccount.google("admin", "admin@example.com", "검토담당");
        MemberBadge existing = badge(member, BadgeType.BRONZE, "4.5");
        when(memberService.requireAdmin(1L)).thenReturn(operator);
        when(memberService.require(7L)).thenReturn(member);
        when(repository.findByMember_IdAndGameVersion(7L, "4.5"))
                .thenReturn(Optional.of(existing));
        BadgeService service = new BadgeService(repository, memberService, CLOCK);

        service.grant(1L, 7L, "4.5", BadgeType.PLATINUM);

        assertThat(existing.getBadgeType()).isEqualTo(BadgeType.PLATINUM);
        assertThat(existing.getLabel()).isEqualTo("이상중재 플래티넘");
        verify(repository).save(existing);
        verify(repository).flush();
        verify(repository, never()).findAllByMember_Id(7L);
    }

    @Test
    void 보유_칭호는_DB_이력을_삭제하지_않고_화면에서만_최신_10개를_보여준다() {
        MemberBadgeRepository repository = mock(MemberBadgeRepository.class);
        MemberAccount member = MemberAccount.google("user", "user@example.com", "유저");
        List<MemberBadge> badges = IntStream.rangeClosed(1, 11)
                .mapToObj(minor -> badge(member, BadgeType.BRONZE, "4." + minor))
                .toList();
        when(repository.findAllByMember_Id(7L)).thenReturn(badges);
        BadgeService service = new BadgeService(repository, mock(MemberService.class), CLOCK);

        List<BadgeView> result = service.activeBadges(7L);

        assertThat(result).hasSize(10);
        assertThat(result.getFirst().version()).isEqualTo("4.11");
        assertThat(result.getLast().version()).isEqualTo("4.2");
    }

    private MemberBadge badge(MemberAccount member, BadgeType type, String version) {
        return new MemberBadge(
                member,
                type,
                version,
                type.label(),
                type.colorHex(),
                null,
                LocalDateTime.ofInstant(CLOCK.instant(), CLOCK.getZone())
        );
    }
}
