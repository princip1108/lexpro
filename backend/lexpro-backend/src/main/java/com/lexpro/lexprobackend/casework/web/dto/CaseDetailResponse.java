package com.lexpro.lexprobackend.casework.web.dto;

import com.lexpro.lexprobackend.casework.domain.CaseRecord;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record CaseDetailResponse(
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
        long creatorId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CaseDetailResponse from(CaseRecord record, boolean overdue) {
        return new CaseDetailResponse(
                record.getCaseId(), record.getCaseName(), record.getCaseNo(), record.getCaseType(),
                record.getCaseCause(), record.getCaseSource(), record.getCurrentStage(), record.getCaseStatus(),
                record.getAcceptDate(), record.getDeadlineAt(), overdue, record.getCreatorId(),
                record.getCreatedAt(), record.getUpdatedAt()
        );
    }
}
