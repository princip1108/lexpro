package com.lexpro.lexprobackend.processing.web.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.domain.DocumentParseResult;

import java.time.OffsetDateTime;

public record DocumentParseDetailResponse(
        Long docId,
        Long dossierId,
        Long caseId,
        Integer versionNo,
        String parseStatus,
        JsonNode parsedText,
        String rawText,
        String parserVersion,
        String errorCode,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        Long requestedBy,
        String requestId,
        JsonNode parserParameters,
        Integer durationMs,
        Boolean current
) {
    public static DocumentParseDetailResponse from(DocumentParseResult result, ObjectMapper objectMapper) {
        return new DocumentParseDetailResponse(result.getDocId(), result.getDossierId(), result.getCaseId(),
                result.getVersionNo(), result.getParseStatus(), parseJson(result.getParsedTextJson(), objectMapper),
                result.getRawText(), result.getParserVersion(), result.getErrorMessage(), result.getCreatedAt(),
                result.getCompletedAt(), result.getRequestedBy(), result.getRequestId(),
                parseJson(result.getParserParametersJson(), objectMapper), result.getDurationMs(), result.getCurrent());
    }

    private static JsonNode parseJson(String value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored processing JSON is invalid", exception);
        }
    }
}
