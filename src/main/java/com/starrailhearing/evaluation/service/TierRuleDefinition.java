package com.starrailhearing.evaluation.service;

public record TierRuleDefinition(
        String code,
        String label,
        String description,
        int score,
        int displayOrder
) {
}
