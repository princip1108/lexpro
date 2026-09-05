package com.lexpro.lexprobackend.recommendation.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TypicalCaseResponse(
        long typicalCaseId,
        String externalCaseId,
        String caseNumber,
        String title,
        String caseCause,
        JsonNode caseCauses,
        String caseType,
        String country,
        String region,
        String court,
        String courtLevel,
        String docType,
        JsonNode disputeFocus,
        LocalDate judgmentDate,
        String procedure,
        JsonNode applicableLaws,
        String caseLevel,
        String sourceName,
        String sourceFile,
        String sourceUrl,
        JsonNode keywords,
        String embeddingModelName,
        String embeddingModelVersion,
        OffsetDateTime embeddedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String content,
        String fact,
        String summary,
        String prosecutorialProcess,
        String adjudicationResult,
        String reasoning,
        String guidingSignificance,
        boolean favorite
) {}
