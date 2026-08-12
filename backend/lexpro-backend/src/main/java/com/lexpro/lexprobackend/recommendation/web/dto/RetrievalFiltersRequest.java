package com.lexpro.lexprobackend.recommendation.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record RetrievalFiltersRequest(
        @Size(max = 255) String caseCause,
        @Size(max = 50) String caseType,
        @Size(max = 50) String courtLevel,
        @Min(1900) @Max(2200) Integer judgmentYearFrom,
        @Min(1900) @Max(2200) Integer judgmentYearTo
) {
    @AssertTrue(message = "judgmentYearFrom must not exceed judgmentYearTo")
    public boolean isYearRangeValid() {
        return judgmentYearFrom == null || judgmentYearTo == null || judgmentYearFrom <= judgmentYearTo;
    }
}
