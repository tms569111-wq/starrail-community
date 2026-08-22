package com.starrailhearing.profile.client;

import com.starrailhearing.character.domain.ProfileProvider;

import java.util.List;

public record PublicGameProfile(
        ProfileProvider provider,
        String uid,
        String nickname,
        String signature,
        boolean displayEnabled,
        List<PublicCharacter> characters
) {
}
