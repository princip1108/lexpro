package com.lexpro.lexprobackend.processing.web.dto;

import com.lexpro.lexprobackend.processing.domain.LegalElementJobRecord;

import java.time.OffsetDateTime;

public record LegalElementJobResponse(
        String requestId,
        Long docId,
        String status,
        Long elementResultId,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
    public static LegalElementJobResponse from(LegalElementJobRecord record) {
        return new LegalElementJobResponse(record.getRequestId(), record.getDocId(), record.getJobStatus(),
                record.getElementResultId(), record.getErrorCode(), record.getRequestedAt(), record.getCompletedAt());
    }
}
