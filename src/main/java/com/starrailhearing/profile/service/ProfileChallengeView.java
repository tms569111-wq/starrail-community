package com.starrailhearing.profile.service;

import java.time.LocalDateTime;

public record ProfileChallengeView(
        String uid,
        String nickname,
        String challengeCode,
        LocalDateTime expiresAt
) {
}
