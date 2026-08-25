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
    private static final String VERIFICATION_STATE_TOKEN = "PROFILE_VERIFICATION";

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
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plus(properties.mihomo().challengeTtl());

        return persistenceService.prepare(
                memberId,
                uid,
                publicProfile,
                VERIFICATION_STATE_TOKEN,
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

    private String validateUid(String rawUid) {
        String uid = rawUid == null ? "" : rawUid.trim();
        if (!uid.matches(UID_PATTERN)) {
            throw new AppException(ErrorCode.INVALID_INPUT, "UID는 숫자 9자리여야 합니다.");
        }
        return uid;
    }

}
