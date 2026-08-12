package com.lexpro.lexprobackend.casework.web.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CaseSummaryResponse(
        long caseId,
        String caseName,
        String caseNo,
        String caseType,
        String caseCause,
        String caseSource,
        String currentStage,
        String caseStatus,
        LocalDate acceptDate,
        OffsetDateTime deadlineAt,
        boolean overdue,
        String handlerName,
        OffsetDateTime updatedAt
) {
}
