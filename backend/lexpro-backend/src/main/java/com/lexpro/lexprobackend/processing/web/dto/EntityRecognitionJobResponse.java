package com.lexpro.lexprobackend.processing.web.dto;

import com.lexpro.lexprobackend.processing.domain.EntityRecognitionJobRecord;

import java.time.OffsetDateTime;

public record EntityRecognitionJobResponse(
        String requestId,
        Long docId,
        String status,
        Long entityResultId,
        String errorCode,
        OffsetDateTime requestedAt,
        OffsetDateTime completedAt
) {
    public static EntityRecognitionJobResponse from(EntityRecognitionJobRecord record) {
        return new EntityRecognitionJobResponse(record.getRequestId(), record.getDocId(), record.getJobStatus(),
                record.getEntityResultId(), record.getErrorCode(), record.getRequestedAt(), record.getCompletedAt());
    }
}
