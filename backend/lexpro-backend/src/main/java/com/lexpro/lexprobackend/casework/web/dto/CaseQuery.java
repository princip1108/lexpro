package com.lexpro.lexprobackend.casework.web.dto;

import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CaseQuery(
        @Min(value = 1, message = "must be at least 1") Integer page,
        @Min(value = 1, message = "must be at least 1")
        @Max(value = 100, message = "must be at most 100") Integer size,
        @Size(max = 255, message = "must not exceed 255 characters") String keyword,
        @Pattern(regexp = "PENDING|PROCESSING|CLOSED|ARCHIVED", message = "must be a supported case status") String status,
        @Size(max = 50, message = "must not exceed 50 characters") String caseType,
        Boolean overdue
) {
    public PageRequest pageRequest() {
        return new PageRequest(page, size);
    }
}
