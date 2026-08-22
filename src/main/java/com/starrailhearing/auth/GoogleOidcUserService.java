package com.starrailhearing.auth;

import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.domain.MemberAccount;
import com.starrailhearing.member.service.MemberService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.HashSet;

@Component
public class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final MemberService memberService;
    private final AppProperties properties;

    public GoogleOidcUserService(
            MemberService memberService,
            AppProperties properties
    ) {
        this.memberService = memberService;
        this.properties = properties;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String email = Boolean.TRUE.equals(oidcUser.getEmailVerified()) ? oidcUser.getEmail() : null;
        boolean operator = isOperator(oidcUser.getSubject());
        MemberAccount member;
        try {
            member = memberService.findOrCreateGoogleMember(
                    oidcUser.getSubject(), email, operator
            );
        } catch (RuntimeException exception) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_unavailable"),
                    exception.getMessage(),
                    exception
            );
        }

        var authorities = new HashSet<GrantedAuthority>(oidcUser.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()));
        if (oidcUser.getUserInfo() == null) {
            return new DefaultOidcUser(authorities, oidcUser.getIdToken(), "sub");
        }
        return new DefaultOidcUser(
                authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), "sub"
        );
    }

    private boolean isOperator(String googleSubject) {
        String configured = properties.operator().subject();
        return configured != null
                && !configured.isBlank()
                && googleSubject != null
                && configured.trim().equals(googleSubject);
    }
}
