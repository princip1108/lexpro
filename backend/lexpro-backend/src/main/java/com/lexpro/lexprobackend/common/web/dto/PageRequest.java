package com.lexpro.lexprobackend.common.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageRequest(
        @Min(value = 1, message = "must be at least 1")
        Integer page,
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 100, message = "must be at most 100")
        Integer size
) {

    public PageRequest {
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }
}
