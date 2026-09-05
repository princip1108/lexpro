package com.lexpro.lexprobackend.recommendation.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateRecommendationAnalysisRequest(
        @Positive Long sourceSummaryId,
        @Size(max = 100000) String factText
) {
    @AssertTrue(message = "exactly one of sourceSummaryId or factText is required")
    public boolean isQueryPresent() {
        return (sourceSummaryId != null) ^ (factText != null && !factText.isBlank());
    }
}
