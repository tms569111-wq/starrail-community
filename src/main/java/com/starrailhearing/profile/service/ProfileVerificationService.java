package com.starrailhearing.profile.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.client.GameProfileClient;
import com.starrailhearing.profile.client.PublicGameProfile;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ProfileVerificationService {

    private static final String UID_PATTERN = "\\d{9}";

    private final ProfilePersistenceService persistenceService;
    private final MemberService memberService;
    private final GameProfileClient profileClient;
    private final AppProperties properties;
    private final Clock clock;
    private final ProfileFetchBulkhead bulkhead;

    public ProfileVerificationService(
            ProfilePersistenceService persistenceService,
            MemberService memberService,
            GameProfileClient profileClient,
            AppProperties properties,
            Clock clock,
            ProfileFetchBulkhead bulkhead
    ) {
        this.persistenceService = persistenceService;
        this.memberService = memberService;
        this.profileClient = profileClient;
        this.properties = properties;
        this.clock = clock;
        this.bulkhead = bulkhead;
    }

    public ProfileSyncResult verifyUid(long memberId, String rawUid) {
        String uid = validateUid(rawUid);
        return bulkhead.execute(() -> {
            memberService.reserveProfileFetch(memberId, properties.mihomo().syncCooldown());
            PublicGameProfile publicProfile = profileClient.fetch(uid, false);
            extendCooldownToProviderTtl(memberId, publicProfile);
            requireCharacters(publicProfile);
            LocalDateTime now = LocalDateTime.now(clock);
            return persistenceService.bindAndVerify(memberId, uid, publicProfile, now);
        });
    }

    public ProfileSyncResult verify(long memberId) {
        return bulkhead.execute(() -> {
            memberService.requireActive(memberId);
            ProfileVerificationContext context = persistenceService.verificationContext(
                    memberId,
                    LocalDateTime.now(clock)
            );
            memberService.reserveProfileFetch(memberId, properties.mihomo().syncCooldown());
            PublicGameProfile publicProfile = profileClient.fetch(context.uid(), true);
            extendCooldownToProviderTtl(memberId, publicProfile);
            requireCharacters(publicProfile);

            return persistenceService.completeVerification(
                    memberId,
                    context.challengeCode(),
                    publicProfile,
                    LocalDateTime.now(clock)
            );
        });
    }

    public ProfileSyncResult refresh(long memberId) {
        return bulkhead.execute(() -> {
            memberService.requireActive(memberId);
            ProfileRefreshContext context = persistenceService.refreshContext(
                    memberId,
                    LocalDateTime.now(clock),
                    properties.mihomo().syncCooldown()
            );
            memberService.reserveProfileFetch(memberId, properties.mihomo().syncCooldown());
            PublicGameProfile publicProfile = profileClient.fetch(context.uid(), true);
            extendCooldownToProviderTtl(memberId, publicProfile);
            requireCharacters(publicProfile);

            return persistenceService.completeRefresh(
                    memberId,
                    publicProfile,
                    LocalDateTime.now(clock),
                    properties.mihomo().syncCooldown()
            );
        });
    }

    public ProfilePageView view(long memberId) {
        return persistenceService.view(memberId, LocalDateTime.now(clock));
    }

    private void requireCharacters(PublicGameProfile publicProfile) {
        if (!publicProfile.displayEnabled() || publicProfile.characters().isEmpty()) {
            throw new AppException(ErrorCode.PROFILE_NOT_PUBLIC);
        }
    }

    private void extendCooldownToProviderTtl(long memberId, PublicGameProfile publicProfile) {
        if (publicProfile.cacheTtlSeconds() <= 0) return;
        java.time.Duration providerTtl = java.time.Duration.ofSeconds(
                publicProfile.cacheTtlSeconds()
        );
        java.time.Duration configuredMaximum = properties.profileClient().maximumCacheTtl();
        if (configuredMaximum != null
                && configuredMaximum.isPositive()
                && providerTtl.compareTo(configuredMaximum) > 0) {
            providerTtl = configuredMaximum;
        }
        if (providerTtl.compareTo(properties.mihomo().syncCooldown()) > 0) {
            memberService.extendProfileFetchCooldown(memberId, providerTtl);
        }
    }

    private String validateUid(String rawUid) {
        String uid = rawUid == null ? "" : rawUid.trim();
        if (!uid.matches(UID_PATTERN)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "UID는 숫자 9자리여야 합니다.");
        }
        return uid;
    }

}
