package com.starrailhearing.vote.service;

public record VoteSubmitResult(
        String optionLabel,
        EidolonFilter countedIn
) {
}
