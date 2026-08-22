package com.starrailhearing.character.service;

import com.starrailhearing.character.domain.CharacterStatus;
import com.starrailhearing.character.domain.ProfileProvider;

import java.util.List;

public record CharacterAdminView(
        long id,
        String canonicalExternalId,
        String slug,
        String name,
        int rarity,
        String pathCode,
        String pathName,
        String elementCode,
        String elementName,
        String iconUrl,
        String portraitUrl,
        int displayOrder,
        CharacterStatus status,
        List<AliasView> aliases
) {
    public record AliasView(long id, ProfileProvider provider, String externalId) {
    }
}
