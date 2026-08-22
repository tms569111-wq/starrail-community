package com.starrailhearing.profile.service;

import com.starrailhearing.profile.domain.ProfileVerificationStatus;

import java.time.LocalDateTime;
import java.util.List;

public record ProfilePageView(
        boolean exists,
        String uid,
        String nickname,
        ProfileVerificationStatus status,
        String challengeCode,
        LocalDateTime challengeExpiresAt,
        LocalDateTime verifiedAt,
        LocalDateTime lastSyncedAt,
        List<VerifiedCharacterView> characters
) {
    public static ProfilePageView empty() {
        return new ProfilePageView(
                false, null, null, null, null, null, null, null, List.of()
        );
    }
}
