package com.lexpro.lexprobackend.processing.web.dto;

import com.lexpro.lexprobackend.processing.domain.CaseSummaryJobRecord;

import java.time.OffsetDateTime;

public record CaseSummaryJobResponse(
        String requestId,
        String summaryType,
        String status,
        Long summaryId,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
    public static CaseSummaryJobResponse from(CaseSummaryJobRecord record) {
        return new CaseSummaryJobResponse(record.getRequestId(), record.getSummaryType(), record.getJobStatus(),
                record.getSummaryId(), record.getErrorCode(), record.getRequestedAt(), record.getCompletedAt());
    }
}
