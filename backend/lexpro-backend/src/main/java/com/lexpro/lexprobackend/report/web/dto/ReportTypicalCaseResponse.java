package com.lexpro.lexprobackend.report.web.dto;

import com.lexpro.lexprobackend.report.domain.ReportTypicalCaseReference;

import java.time.OffsetDateTime;

public record ReportTypicalCaseResponse(
        Long typicalCaseId,
        Long recommendationItemId,
        String sectionCode,
        String citationNote,
        Integer sortNo,
        Long citedBy,
        OffsetDateTime createdAt
) {
    public static ReportTypicalCaseResponse from(ReportTypicalCaseReference reference) {
        return new ReportTypicalCaseResponse(reference.typicalCaseId(), reference.recommendationItemId(),
                reference.sectionCode(), reference.citationNote(), reference.sortNo(), reference.citedBy(),
                reference.createdAt());
    }
}
