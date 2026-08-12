package com.lexpro.lexprobackend.report.domain;

import java.time.OffsetDateTime;

public record CaseCardJobRecord(
        String requestId,
        Long fillTaskId,
        String fillMode,
        String jobStatus,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
}
