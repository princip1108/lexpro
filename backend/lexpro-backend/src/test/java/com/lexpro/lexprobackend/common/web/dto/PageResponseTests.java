package com.lexpro.lexprobackend.common.web.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageResponseTests {

    @Test
    void shouldMapMybatisPageWithoutExposingMutableItems() {
        Page<String> source = new Page<>(2, 2, 5);
        source.setRecords(List.of("a", "b"));

        PageResponse<Integer> response = PageResponse.from(source, String::length);

        assertEquals(List.of(1, 1), response.items());
        assertEquals(2, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalItems());
        assertEquals(3, response.totalPages());
        assertThrows(UnsupportedOperationException.class, () -> response.items().add(2));
    }
}
