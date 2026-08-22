package com.starrailhearing.member.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class NicknamePolicy {
    private static final Set<String> RESERVED_CONTAINS = Set.of(
            "관리자", "운영자", "운영진", "admin", "administrator", "staff",
            "hoyoverse", "mihoyo", "official", "공식"
    );
    private static final Set<String> RESERVED_EXACT = Set.of("gm");

    private NicknamePolicy() {
    }

    public static String display(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 50) {
            throw new IllegalArgumentException("닉네임은 1~50자여야 합니다.");
        }
        if (!normalized.matches("[\\p{L}\\p{N} _\\-·.]+")) {
            throw new IllegalArgumentException("닉네임에는 문자, 숫자, 공백, -, _, ·, .만 사용할 수 있습니다.");
        }
        String compactKey = key(normalized).replaceAll("[\\s._·-]", "");
        if (RESERVED_EXACT.contains(compactKey)
                || RESERVED_CONTAINS.stream().anyMatch(compactKey::contains)) {
            throw new IllegalArgumentException("운영자를 사칭할 수 있는 닉네임은 사용할 수 없습니다.");
        }
        return normalized;
    }

    public static String key(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
