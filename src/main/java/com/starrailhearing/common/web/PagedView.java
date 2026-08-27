package com.starrailhearing.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public record PagedView<T>(
        List<T> items,
        int page,
        int totalPages,
        long totalElements
) {
    public static <S, T> PagedView<T> from(
            Page<S> source,
            Function<S, T> mapper,
            int maximumPages
    ) {
        int visiblePages = Math.min(maximumPages, source.getTotalPages());
        long visibleElements = Math.min(
                source.getTotalElements(),
                (long) maximumPages * source.getSize()
        );
        return new PagedView<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                visiblePages,
                visibleElements
        );
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return page + 1 < totalPages;
    }

    public List<Integer> pageNumbers() {
        return IntStream.range(0, totalPages).boxed().toList();
    }
}
