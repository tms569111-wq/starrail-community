package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;

import java.time.LocalDateTime;

public record ProfileProviderHealthView(
        ProfileProvider provider,
        boolean circuitOpen,
        int consecutiveFailures,
        LocalDateTime retryAt
) {
}
