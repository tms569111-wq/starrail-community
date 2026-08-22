package com.starrailhearing.member.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberAccountTest {

    @Test
    void 구글_회원은_프로필과_역할을_갱신할_수_있다() {
        MemberAccount member = MemberAccount.google("google-sub", "USER@EXAMPLE.COM", "  개척자  ");

        member.synchronizeRole(true);
        member.updateGoogleProfile("NEW@EXAMPLE.COM");

        assertThat(member.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(member.getNickname()).isEqualTo("개척자");
        assertThat(member.getEmail()).isEqualTo("new@example.com");
        assertThat(member.isAdmin()).isTrue();
    }

    @Test
    void 운영자_계정은_작성정지할_수_없다() {
        MemberAccount member = MemberAccount.google("operator", "admin@example.com", "별빛 개척자");
        member.synchronizeRole(true);

        assertThatThrownBy(() -> member.suspendPermanently("운영 정책"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("운영자");
    }

    @Test
    void 구글_실명과_무관하게_최초_닉네임_설정이_필요하다() {
        MemberAccount member = MemberAccount.google("stable-sub", "user@example.com", "개척자-ABC123");

        assertThat(member.isNicknameConfigured()).isFalse();
    }

    @Test
    void 탈퇴한_계정은_제재로_다시_활성상태가_되지_않는다() {
        MemberAccount member = new MemberAccount("탈퇴 테스트");
        setId(member, 77L);
        member.withdraw(LocalDateTime.of(2026, 8, 18, 12, 0));

        assertThatThrownBy(() -> member.suspendPermanently("재제재"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("탈퇴");
    }

    private void setId(MemberAccount member, long id) {
        try {
            var field = MemberAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(member, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
