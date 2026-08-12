package com.lexpro.lexprobackend.report.web.dto;

import com.lexpro.lexprobackend.report.domain.ReportJobRecord;

import java.time.OffsetDateTime;

public record ReportJobResponse(
        String requestId,
        Long reportId,
        String reportType,
        String status,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
    public static ReportJobResponse from(ReportJobRecord record) {
        return new ReportJobResponse(record.requestId(), record.reportId(), record.reportType(),
                record.jobStatus(), record.errorCode(), record.requestedAt(), record.completedAt());
    }
}
