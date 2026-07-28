package com.lexpro.lexprobackend.common.web.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(
        List<T> items,
        long page,
        long size,
        long totalItems,
        long totalPages
) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <S, T> PageResponse<T> from(IPage<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getRecords().stream().map(mapper).toList(),
                source.getCurrent(),
                source.getSize(),
                source.getTotal(),
                source.getPages()
        );
    }
}
