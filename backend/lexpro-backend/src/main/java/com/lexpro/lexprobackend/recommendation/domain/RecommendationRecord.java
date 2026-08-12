package com.lexpro.lexprobackend.recommendation.domain;

import java.time.OffsetDateTime;

public record RecommendationRecord(
        long recommendId,
        long caseId,
        Long sourceSummaryId,
        String queryFactText,
        String queryDisputeFocusJson,
        String modelName,
        String modelVersion,
        String promptVersion,
        String queryParametersJson,
        String requestId,
        Integer durationMs,
        Long createdBy,
        OffsetDateTime createdAt
) {}
