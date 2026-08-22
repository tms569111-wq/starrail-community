package com.starrailhearing.member.service;

import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.repository.MemberAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
