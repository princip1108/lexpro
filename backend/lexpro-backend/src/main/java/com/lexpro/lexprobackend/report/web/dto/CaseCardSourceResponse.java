package com.lexpro.lexprobackend.report.web.dto;

import com.lexpro.lexprobackend.report.domain.CaseCardTaskSource;

import java.time.OffsetDateTime;

public record CaseCardSourceResponse(
        Long sourceId,
        String sourceType,
        Long sourceResultId,
        OffsetDateTime createdAt
) {
    public static CaseCardSourceResponse from(CaseCardTaskSource source) {
        return new CaseCardSourceResponse(source.sourceId(), source.sourceType(),
                source.sourceResultId(), source.createdAt());
    }
}
