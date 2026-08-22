package com.starrailhearing.vote.service;

public record CharacterCardView(
        Long id,
        String slug,
        String name,
        int rarity,
        String pathName,
        String elementCode,
        String elementName,
        String iconUrl,
        long voteCount,
        String tier,
        boolean sampleSufficient
) {
}
