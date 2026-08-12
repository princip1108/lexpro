package com.lexpro.lexprobackend.report.domain;

import java.time.OffsetDateTime;

public record ReportTypicalCaseReference(
        Long typicalCaseId,
        Long recommendationItemId,
        String sectionCode,
        String citationNote,
        Integer sortNo,
        Long citedBy,
        OffsetDateTime createdAt
) {
}
