package com.lexpro.lexprobackend.report.domain;

import java.time.OffsetDateTime;

public record CaseCardTaskSource(
        Long sourceId,
        String sourceType,
        Long sourceResultId,
        OffsetDateTime createdAt
) {
}
