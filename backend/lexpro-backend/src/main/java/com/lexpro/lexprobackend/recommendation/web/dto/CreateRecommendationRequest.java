package com.lexpro.lexprobackend.recommendation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRecommendationRequest(
        @Positive Long sourceSummaryId,
        @Size(max = 100000) String factText,
        @Size(max = 100) List<@Size(max = 500) String> disputeFocus,
        @Valid RetrievalFiltersRequest filters,
        @Min(1) @Max(50) Integer limit
) {
    @AssertTrue(message = "exactly one of sourceSummaryId or factText is required")
    public boolean isQueryPresent() {
        return (sourceSummaryId != null) ^ (factText != null && !factText.isBlank());
    }
}
