package com.starrailhearing.evaluation.service;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TierRuleCatalog {
    private static final Map<String, Integer> REQUIRED_SCORES = Map.of(
            "T0", 5, "T05", 4, "T1", 3, "T15", 2, "T2", 1
    );
    private final ObjectMapper objectMapper;

    public TierRuleCatalog(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TierVersionRules load(String versionCode) {
        String normalizedVersion = versionCode == null ? "" : versionCode.trim();
        if (!normalizedVersion.matches("[0-9]+(\\.[0-9]+){1,2}")) {
            throw new AppException(ErrorCode.INVALID_INPUT, "게임 버전은 4.4 또는 4.4.1 형식이어야 합니다.");
        }
        ClassPathResource resource = new ClassPathResource(
                "tier-rules/" + normalizedVersion + ".yml"
        );
        if (!resource.exists()) {
            throw new AppException(ErrorCode.TIER_RULE_NOT_FOUND);
        }
        try (InputStream input = resource.getInputStream()) {
            Object loaded = new Yaml().load(input);
            if (!(loaded instanceof Map<?, ?> raw)) {
                throw new IllegalArgumentException("티어 규칙 YAML의 최상위 값은 객체여야 합니다.");
            }
            String version = text(raw.get("version"));
            int minimumSample = integer(raw.get("minimum-sample"));
            List<TierRuleDefinition> tiers = parseTiers(raw.get("tiers"));
            Map<String, Map<String, String>> copies = parseCopies(raw.get("character-copy"));
            validate(normalizedVersion, version, minimumSample, tiers, copies);
            return new TierVersionRules(
                    version,
                    minimumSample,
                    List.copyOf(tiers),
                    Map.copyOf(copies),
                    objectMapper.writeValueAsString(raw)
            );
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(
                    ErrorCode.INVALID_INPUT,
                    "티어 규칙 파일을 읽을 수 없습니다: " + exception.getMessage(),
                    exception
            );
        }
    }

    private List<TierRuleDefinition> parseTiers(Object value) {
        if (!(value instanceof List<?> rows)) return List.of();
        List<TierRuleDefinition> result = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) continue;
            result.add(new TierRuleDefinition(
                    text(map.get("code")),
                    text(map.get("label")),
                    text(map.get("description")),
                    integer(map.get("score")),
                    integer(map.get("display-order"))
            ));
        }
        return result;
    }

    private Map<String, Map<String, String>> parseCopies(Object value) {
        if (!(value instanceof Map<?, ?> rows)) return Map.of();
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> row : rows.entrySet()) {
            if (!(row.getValue() instanceof Map<?, ?> copyMap)) continue;
            Map<String, String> copy = new LinkedHashMap<>();
            copyMap.forEach((key, text) -> copy.put(String.valueOf(key), String.valueOf(text)));
            result.put(String.valueOf(row.getKey()), Map.copyOf(copy));
        }
        return result;
    }

    private void validate(
            String requested,
            String actual,
            int minimumSample,
            List<TierRuleDefinition> tiers,
            Map<String, Map<String, String>> copies
    ) {
        if (!requested.equals(actual)) throw new IllegalArgumentException("YAML 버전과 요청 버전이 다릅니다.");
        if (minimumSample < 1 || minimumSample > 10000) {
            throw new IllegalArgumentException("최소 표본은 1~10000 사이여야 합니다.");
        }
        if (tiers.size() != 5) throw new IllegalArgumentException("티어 선택지는 정확히 5개여야 합니다.");
        if (tiers.stream().map(TierRuleDefinition::code).distinct().count() != tiers.size()) {
            throw new IllegalArgumentException("티어 코드가 중복되었습니다.");
        }
        Map<String, Integer> actualScores = tiers.stream().collect(java.util.stream.Collectors.toMap(
                TierRuleDefinition::code, TierRuleDefinition::score
        ));
        if (!actualScores.equals(REQUIRED_SCORES)) {
            throw new IllegalArgumentException("티어 코드와 점수는 T0=5, T05=4, T1=3, T15=2, T2=1이어야 합니다.");
        }
        if (tiers.stream().map(TierRuleDefinition::displayOrder).distinct().count() != 5
                || tiers.stream().anyMatch(tier -> tier.displayOrder() < 1 || tier.displayOrder() > 5)
                || tiers.stream().anyMatch(tier -> tier.label().isBlank() || tier.description().isBlank())) {
            throw new IllegalArgumentException("티어 표시 순서·라벨·설명을 확인해 주세요.");
        }
        boolean invalidCopy = copies.values().stream()
                .flatMap(copy -> copy.keySet().stream())
                .anyMatch(code -> !REQUIRED_SCORES.containsKey(code));
        if (invalidCopy) throw new IllegalArgumentException("캐릭터별 문구에 알 수 없는 티어 코드가 있습니다.");
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(text(value));
    }
}
