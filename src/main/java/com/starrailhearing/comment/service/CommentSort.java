package com.starrailhearing.comment.service;

import org.springframework.data.domain.Sort;

public enum CommentSort {
    RECOMMENDED("추천순", Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"))),
    LATEST("최신순", Sort.by(Sort.Order.desc("createdAt"))),
    OLDEST("등록순", Sort.by(Sort.Order.asc("createdAt")));

    private final String label;
    private final Sort sort;

    CommentSort(String label, Sort sort) {
        this.label = label;
        this.sort = sort;
    }

    public String getLabel() {
        return label;
    }

    public Sort toSort() {
        return sort;
    }

    public static CommentSort from(String value) {
        if (value == null || value.isBlank()) return RECOMMENDED;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return RECOMMENDED;
        }
    }
}
