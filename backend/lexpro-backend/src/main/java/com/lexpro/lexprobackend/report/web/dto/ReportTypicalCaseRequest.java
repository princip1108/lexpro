package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReportTypicalCaseRequest(
        @Positive long typicalCaseId,
        @Positive Long recommendationItemId,
        @Size(max = 100) String sectionCode,
        @Size(max = 5000) String citationNote,
        @Positive Integer sortNo
) {
}
