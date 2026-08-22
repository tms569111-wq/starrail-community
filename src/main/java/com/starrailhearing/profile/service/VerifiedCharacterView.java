package com.starrailhearing.profile.service;

import java.time.LocalDateTime;

public record VerifiedCharacterView(
        String slug,
        String name,
        String iconUrl,
        int eidolon,
        LocalDateTime lastVerifiedAt
) {
}
