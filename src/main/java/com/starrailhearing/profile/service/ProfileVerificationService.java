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
    private static final String REQUIRED_SIGNATURE = "투표!";

    private final ProfilePersistenceService persistenceService;
    private final MemberService memberService;
    private final GameProfileClient profileClient;
    private final AppProperties properties;
    private final Clock clock;

    public ProfileVerificationService(
            ProfilePersistenceService persistenceService,
            MemberService memberService,
            GameProfileClient profileClient,
            AppProperties properties,
            Clock clock
    ) {
        this.persistenceService = persistenceService;
        this.memberService = memberService;
        this.profileClient = profileClient;
        this.properties = properties;
        this.clock = clock;
    }

    public ProfileChallengeView prepare(long memberId, String rawUid) {
        memberService.requireActiveForWrite(memberId);
        String uid = validateUid(rawUid);
        PublicGameProfile publicProfile = profileClient.fetch(uid, false);
        requireChallengeSignature(publicProfile, REQUIRED_SIGNATURE);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(properties.mihomo().challengeTtl());

        return persistenceService.prepare(
                memberId,
                uid,
                publicProfile,
                REQUIRED_SIGNATURE,
                expiresAt
        );
    }

    public ProfileSyncResult verify(long memberId) {
        memberService.requireActiveForWrite(memberId);
        ProfileVerificationContext context = persistenceService.verificationContext(
                memberId,
                LocalDateTime.now(clock)
        );
        PublicGameProfile publicProfile = profileClient.fetch(context.uid(), true);
        requireChallengeSignature(publicProfile, context.challengeCode());
        requireCharacters(publicProfile);

        return persistenceService.completeVerification(
                memberId,
                context.challengeCode(),
                publicProfile,
                LocalDateTime.now(clock)
        );
    }

    public ProfileSyncResult refresh(long memberId) {
        memberService.requireActiveForWrite(memberId);
        ProfileRefreshContext context = persistenceService.refreshContext(
                memberId,
                LocalDateTime.now(clock),
                properties.mihomo().syncCooldown()
        );
        PublicGameProfile publicProfile = profileClient.fetch(context.uid(), true);
        requireCharacters(publicProfile);

        return persistenceService.completeRefresh(
                memberId,
                publicProfile,
                LocalDateTime.now(clock),
                properties.mihomo().syncCooldown()
        );
    }

    public ProfilePageView view(long memberId) {
        return persistenceService.view(memberId);
    }

    private void requireCharacters(PublicGameProfile publicProfile) {
        if (!publicProfile.displayEnabled() || publicProfile.characters().isEmpty()) {
            throw new AppException(ErrorCode.PROFILE_NOT_PUBLIC);
        }
    }

    private void requireChallengeSignature(PublicGameProfile publicProfile, String challengeCode) {
        String signature = publicProfile.signature();
        if (signature == null || !signature.contains(challengeCode)) {
            throw new AppException(ErrorCode.PROFILE_SIGNATURE_MISMATCH);
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
