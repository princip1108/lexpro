package com.lexpro.lexprobackend.processing.web.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.domain.LegalElementResult;

import java.time.OffsetDateTime;

public record LegalElementResultResponse(
        Long elementResultId,
        Long docId,
        Long caseId,
        String caseCause,
        JsonNode originalElements,
        JsonNode finalElements,
        JsonNode validationReport,
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
    public static LegalElementResultResponse from(LegalElementResult result, ObjectMapper objectMapper) {
        return new LegalElementResultResponse(result.getElementResultId(), result.getDocId(), result.getCaseId(),
                result.getCaseCause(), parseJson(result.getRawElementsJson(), objectMapper),
                parseJson(result.getFinalElementsJson(), objectMapper),
                parseJson(result.getValidationReportJson(), objectMapper), result.getModelName(),
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
            throw new IllegalStateException("Stored legal-element JSON is invalid", exception);
        }
    }
}
