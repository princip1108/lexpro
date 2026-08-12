package com.lexpro.lexprobackend.recommendation.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TypicalCaseRecord(
        long typicalCaseId,
        String externalCaseId,
        String title,
        String caseCause,
        String caseCauseFullJson,
        String caseType,
        String country,
        String court,
        String courtLevel,
        String docType,
        String disputeFocusJson,
        LocalDate judgmentDate,
        String procedure,
        String applicableLawJson,
        String caseLevel,
        String embeddingModelName,
        String embeddingModelVersion,
        OffsetDateTime embeddedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String content,
        String fact,
        boolean favorite
) {}
