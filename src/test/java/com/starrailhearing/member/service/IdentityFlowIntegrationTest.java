package com.starrailhearing.member.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.repository.MemberAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class IdentityFlowIntegrationTest {

    @Autowired
    MemberService memberService;

    @Autowired
    MemberAccountRepository memberRepository;

    @Test
    void 첫_구글_로그인은_sub로_회원을_만들고_구글_이름을_공개하지_않는다() {
        MemberAccount created = memberService.findOrCreateGoogleMember(
                "operator-google-subject", "operator@example.com", "운영자", true
        );

        MemberAccount sameMember = memberService.findOrCreateGoogleMember(
                "operator-google-subject", "changed@example.com", "다른 이름", true
        );

        assertThat(sameMember.getId()).isEqualTo(created.getId());
        assertThat(sameMember.isAdmin()).isTrue();
        assertThat(sameMember.getEmail()).isEqualTo("changed@example.com");
        assertThat(sameMember.getNickname()).startsWith("개척자-");
        assertThat(sameMember.isNicknameConfigured()).isFalse();
        assertThat(memberRepository.findById(created.getId())).isPresent();
    }

    @Test
    void 임시_닉네임_상태에서도_UID_조회는_예약되고_연속_요청은_차단된다() {
        MemberAccount member = memberService.findOrCreateGoogleMember(
                "profile-cooldown-subject", "profile@example.com", false
        );

        memberService.reserveProfileFetch(member.getId(), Duration.ofMinutes(3));

        assertThat(member.isNicknameConfigured()).isFalse();
        assertThat(member.getProfileFetchAvailableAt()).isNotNull();
        assertThatThrownBy(() -> memberService.reserveProfileFetch(
                member.getId(), Duration.ofMinutes(3)
        ))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PROFILE_SYNC_COOLDOWN)
                );
    }
}
