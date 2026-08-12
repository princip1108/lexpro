package com.lexpro.lexprobackend.report.web.dto;

import com.lexpro.lexprobackend.report.domain.CaseCardJobRecord;

import java.time.OffsetDateTime;

public record CaseCardJobResponse(
        String requestId,
        Long fillTaskId,
        String fillMode,
        String status,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
    public static CaseCardJobResponse from(CaseCardJobRecord record) {
        return new CaseCardJobResponse(record.requestId(), record.fillTaskId(), record.fillMode(),
                record.jobStatus(), record.errorCode(), record.requestedAt(), record.completedAt());
    }
}
