package com.lexpro.lexprobackend.recommendation.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecommendationItemRecord(
        long itemId,
        long typicalCaseId,
        int rankNo,
        BigDecimal similarityScore,
        BigDecimal rankingScore,
        String reasonJson,
        String externalCaseId,
        String caseNumber,
        String title,
        String caseCause,
        String caseCauseFullJson,
        String caseType,
        String region,
        String court,
        String courtLevel,
        String docType,
        String applicableLawJson,
        String caseLevel,
        LocalDate judgmentDate,
        boolean favorite
) {}
