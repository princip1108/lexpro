package com.lexpro.lexprobackend.processing.web.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryResult;

import java.time.OffsetDateTime;

public record CaseSummaryResponse(
        Long summaryId,
        Long caseId,
        String summaryType,
        String summaryText,
        Integer versionNo,
        Boolean current,
        String modelName,
        String modelVersion,
        String promptVersion,
        String schemaVersion,
        JsonNode generationParameters,
        JsonNode tokenUsage,
        String requestId,
        Integer durationMs,
        Long createdBy,
        OffsetDateTime createdAt,
        Long confirmedBy,
        OffsetDateTime confirmedAt
) {
    public static CaseSummaryResponse from(CaseSummaryResult result, ObjectMapper objectMapper) {
        return new CaseSummaryResponse(result.getSummaryId(), result.getCaseId(), result.getSummaryType(),
                result.getSummaryText(), result.getVersionNo(), result.getCurrent(), result.getModelName(),
                result.getModelVersion(), result.getPromptVersion(), result.getSchemaVersion(),
                parseJson(result.getGenerationParametersJson(), objectMapper),
                parseJson(result.getTokenUsageJson(), objectMapper), result.getRequestId(), result.getDurationMs(),
                result.getCreatedBy(), result.getCreatedAt(), result.getConfirmedBy(), result.getConfirmedAt());
    }

    private static JsonNode parseJson(String value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored case-summary JSON is invalid", exception);
        }
    }
}
