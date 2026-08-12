package com.lexpro.lexprobackend.report.domain;

import java.time.OffsetDateTime;

public record ReportJobRecord(
        String requestId,
        Long reportId,
        String reportType,
        String jobStatus,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
}
