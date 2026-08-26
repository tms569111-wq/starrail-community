package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;

import java.util.List;

public record PublicGameProfile(
        ProfileProvider provider,
        String uid,
        String nickname,
        String signature,
        boolean displayEnabled,
        List<PublicCharacter> characters,
        long cacheTtlSeconds
) {
    public PublicGameProfile(
            ProfileProvider provider,
            String uid,
            String nickname,
            String signature,
            boolean displayEnabled,
            List<PublicCharacter> characters
    ) {
        this(provider, uid, nickname, signature, displayEnabled, characters, 0);
    }

    public PublicGameProfile withCacheTtlSeconds(long remainingSeconds) {
        return new PublicGameProfile(
                provider,
                uid,
                nickname,
                signature,
                displayEnabled,
                characters,
                Math.max(0, remainingSeconds)
        );
    }
}
