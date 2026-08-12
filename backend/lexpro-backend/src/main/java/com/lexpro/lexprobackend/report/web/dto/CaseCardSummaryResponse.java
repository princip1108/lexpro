package com.lexpro.lexprobackend.report.web.dto;

import com.lexpro.lexprobackend.report.domain.CaseCardFillTask;

import java.time.OffsetDateTime;

public record CaseCardSummaryResponse(
        Long fillTaskId,
        Long caseId,
        String fillMode,
        String fillStatus,
        String modelName,
        String modelVersion,
        String promptVersion,
        String requestId,
        Integer durationMs,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        Long confirmedBy,
        OffsetDateTime confirmedAt,
        String errorCode
) {
    public static CaseCardSummaryResponse from(CaseCardFillTask task) {
        return new CaseCardSummaryResponse(task.getFillTaskId(), task.getCaseId(), task.getFillMode(),
                task.getFillStatus(), task.getModelName(), task.getModelVersion(), task.getPromptVersion(),
                task.getRequestId(), task.getDurationMs(), task.getCreatedAt(), task.getCompletedAt(),
                task.getConfirmedBy(), task.getConfirmedAt(), task.getErrorMessage());
    }
}
