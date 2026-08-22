package com.starrailhearing.web;

import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.repository.MemberAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class WebSmokeTest {
    private static final String OPERATOR_SUBJECT = "web-smoke-operator";

    @Autowired
    WebApplicationContext context;

    @Autowired
    MemberAccountRepository memberRepository;

    MockMvc mockMvc;
    MemberAccount operator;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        operator = memberRepository
                .findByAuthProviderAndProviderUserId(
                        com.starrailhearing.member.domain.AuthProvider.GOOGLE,
                        OPERATOR_SUBJECT
                )
                .orElseGet(() -> {
                    MemberAccount created = MemberAccount.google(
                            OPERATOR_SUBJECT, "admin@example.com", "테스트 개척자"
                    );
                    created.synchronizeRole(true);
                    return memberRepository.save(created);
                });
    }

    @Test
    void 공개_홈과_로그인_화면을_열_수_있다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("붕스청문회")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tier-board")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("전체 캐릭터 티어표")));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Google 계정으로 계속")));
    }

    @Test
    void 익명_사용자는_내_계정에서_로그인_화면으로_이동한다() throws Exception {
        mockMvc.perform(get("/me/account"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void 운영자는_관리자_화면을_열고_닉네임을_바꿀_수_있다() throws Exception {
        mockMvc.perform(get("/admin").with(operatorLogin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("운영자 센터")));

        mockMvc.perform(post("/me/account/nickname")
                        .with(operatorLogin())
                        .with(csrf())
                        .param("nickname", "은하 중재자"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/me/account"));

        assertThat(memberRepository.findById(operator.getId()).orElseThrow().getNickname())
                .isEqualTo("은하 중재자");
    }

    private RequestPostProcessor operatorLogin() {
        return oidcLogin()
                .idToken(token -> token.subject(OPERATOR_SUBJECT))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                );
    }
}
