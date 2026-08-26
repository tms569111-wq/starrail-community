package com.starrailhearing.profile.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import com.starrailhearing.config.AppProperties;
import com.starrailhearing.member.service.MemberService;
import com.starrailhearing.profile.client.GameProfileClient;
import com.starrailhearing.profile.client.PublicGameProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ProfileVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ProfileVerificationService.class);
    private static final String UID_PATTERN = "\\d{9}";

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

    public ProfileSyncResult verifyUid(long memberId, String rawUid) {
        String uid = validateUid(rawUid);
        PublicGameProfile publicProfile = fetchWithReservation(memberId, uid, false);
        requireCharacters(publicProfile);
        LocalDateTime now = LocalDateTime.now(clock);
        return persistenceService.bindAndVerify(memberId, uid, publicProfile, now);
    }

    public ProfileSyncResult verify(long memberId) {
        memberService.requireActive(memberId);
        ProfileVerificationContext context = persistenceService.verificationContext(
                memberId,
                LocalDateTime.now(clock)
        );
        PublicGameProfile publicProfile = fetchWithReservation(memberId, context.uid(), true);
        requireCharacters(publicProfile);

        return persistenceService.completeVerification(
                memberId,
                context.challengeCode(),
                publicProfile,
                LocalDateTime.now(clock)
        );
    }

    public ProfileSyncResult refresh(long memberId) {
        memberService.requireActive(memberId);
        ProfileRefreshContext context = persistenceService.refreshContext(
                memberId,
                LocalDateTime.now(clock),
                properties.mihomo().syncCooldown()
        );
        PublicGameProfile publicProfile = fetchWithReservation(memberId, context.uid(), true);
        requireCharacters(publicProfile);

        return persistenceService.completeRefresh(
                memberId,
                publicProfile,
                LocalDateTime.now(clock),
                properties.mihomo().syncCooldown()
        );
    }

    public ProfilePageView view(long memberId) {
        return persistenceService.view(memberId, LocalDateTime.now(clock));
    }

    private PublicGameProfile fetchWithReservation(long memberId, String uid, boolean forceUpdate) {
        LocalDateTime reservedUntil = memberService.reserveProfileFetch(
                memberId,
                properties.mihomo().syncCooldown()
        );
        try {
            return profileClient.fetch(uid, forceUpdate);
        } catch (AppException exception) {
            if (shouldReleaseReservation(exception.getErrorCode())) {
                memberService.releaseProfileFetchReservation(memberId, reservedUntil);
                log.info(
                        "Released profile fetch cooldown after infrastructure failure memberId={} errorCode={}",
                        memberId,
                        exception.getErrorCode()
                );
            }
            throw exception;
        }
    }

    private boolean shouldReleaseReservation(ErrorCode errorCode) {
        return errorCode == ErrorCode.UPSTREAM_UNAVAILABLE
                || errorCode == ErrorCode.PROFILE_REQUEST_THROTTLED
                || errorCode == ErrorCode.PROFILE_SYNC_COOLDOWN;
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
