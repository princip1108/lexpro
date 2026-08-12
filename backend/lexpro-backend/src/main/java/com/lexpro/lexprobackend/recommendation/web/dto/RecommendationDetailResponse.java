package com.lexpro.lexprobackend.recommendation.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record RecommendationDetailResponse(
        long recommendId,
        long caseId,
        Long sourceSummaryId,
        String queryFactText,
        JsonNode queryDisputeFocus,
        String modelName,
        String modelVersion,
        String pipelineVersion,
        JsonNode parameters,
        String requestId,
        Integer durationMs,
        OffsetDateTime createdAt,
        List<Item> items
) {
    public record Item(
            long itemId,
            long typicalCaseId,
            int rank,
            BigDecimal score,
            JsonNode reasons,
            String externalCaseId,
            String title,
            String caseCause,
            String court,
            String courtLevel,
            LocalDate judgmentDate,
            boolean favorite
    ) {}
}
