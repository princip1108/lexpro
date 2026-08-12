package com.lexpro.lexprobackend.recommendation.web.dto;

import java.time.OffsetDateTime;

public record RecommendationSummaryResponse(
        long recommendId,
        Long sourceSummaryId,
        String modelName,
        String modelVersion,
        String pipelineVersion,
        String requestId,
        Integer durationMs,
        OffsetDateTime createdAt
) {}
