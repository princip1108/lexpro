package com.lexpro.lexprobackend.recommendation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecommendationItemRecord(
        long itemId,
        long typicalCaseId,
        int rankNo,
        BigDecimal similarityScore,
        String reasonJson,
        String externalCaseId,
        String title,
        String caseCause,
        String court,
        String courtLevel,
        LocalDate judgmentDate,
        boolean favorite
) {}
