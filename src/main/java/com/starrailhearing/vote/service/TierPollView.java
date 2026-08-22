package com.starrailhearing.vote.service;

import java.util.List;

public record TierPollView(
        String gameVersion,
        int minimumSample,
        EidolonFilter filter,
        long totalVotes,
        String aggregateTier,
        boolean sampleSufficient,
        boolean canVote,
        Integer verifiedEidolon,
        List<TierOptionView> options
) {
}
