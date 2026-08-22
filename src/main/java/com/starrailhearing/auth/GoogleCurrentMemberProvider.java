package com.starrailhearing.auth;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.member.domain.AuthProvider;
import com.starrailhearing.member.repository.MemberAccountRepository;
import com.starrailhearing.member.service.CurrentMemberProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class GoogleCurrentMemberProvider implements CurrentMemberProvider {
    private final MemberAccountRepository repository;

    public GoogleCurrentMemberProvider(MemberAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Long> findCurrentMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return Optional.empty();
        }
        var member = repository.findByAuthProviderAndProviderUserId(
                        AuthProvider.GOOGLE, oidcUser.getSubject()
                )
                .orElseThrow(() -> new AppException(ErrorCode.MEMBER_NOT_FOUND));
        if (member.isDeleted()) throw new AppException(ErrorCode.ACCOUNT_NOT_ACTIVE);
        return Optional.of(member.getId());
    }
}
