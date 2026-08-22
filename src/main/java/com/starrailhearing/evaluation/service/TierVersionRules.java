package com.starrailhearing.evaluation.service;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

public record TierVersionRules(
        String version,
        int minimumSample,
        List<TierRuleDefinition> tiers,
        Map<String, Map<String, String>> characterCopy,
        String snapshotJson
) {
    public String characterCopyJson(String slug, ObjectMapper objectMapper) {
        Map<String, String> copy = characterCopy.getOrDefault(slug, Map.of());
        try {
            return objectMapper.writeValueAsString(copy);
        } catch (Exception exception) {
            throw new IllegalStateException("캐릭터별 티어 문구를 직렬화할 수 없습니다.", exception);
        }
    }
}
