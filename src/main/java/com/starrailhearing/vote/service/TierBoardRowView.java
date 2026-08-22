package com.starrailhearing.vote.service;

import java.util.List;

public record TierBoardRowView(
        String key,
        String label,
        String description,
        List<CharacterCardView> characters
) {
}
