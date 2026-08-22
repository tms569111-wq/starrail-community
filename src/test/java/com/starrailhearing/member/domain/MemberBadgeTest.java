package com.starrailhearing.member.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberBadgeTest {

    @Test
    void 버전별_칭호의_문구와_색상을_보존한다() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 15, 12, 0);
        MemberAccount member = MemberAccount.google("user", "user@example.com", "유저");
        MemberAccount operator = MemberAccount.google("admin", "admin@example.com", "별빛 개척자");
        MemberBadge badge = new MemberBadge(
                member, BadgeType.PLATINUM, "4.4", "이상중재 PLATINUM", "#8de9ff", operator, now
        );

        assertThat(badge.getGameVersion()).isEqualTo("4.4");
        assertThat(badge.getLabel()).isEqualTo("이상중재 PLATINUM");
        assertThat(badge.getColorHex()).isEqualTo("#8DE9FF");
        assertThat(badge.isActive()).isTrue();
    }

    @Test
    void 색상은_육자리_16진수만_허용한다() {
        MemberAccount member = MemberAccount.google("user", "user@example.com", "유저");

        assertThatThrownBy(() -> new MemberBadge(
                member, BadgeType.PLATINUM, "4.4", "PLATINUM", "blue", null, LocalDateTime.now()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
