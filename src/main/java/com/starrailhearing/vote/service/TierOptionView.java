package com.starrailhearing.vote.service;

public record TierOptionView(
        Long id,
        String code,
        String label,
        String description,
        String characterCopy,
        long count,
        int percentage,
        boolean selected
) {
}
