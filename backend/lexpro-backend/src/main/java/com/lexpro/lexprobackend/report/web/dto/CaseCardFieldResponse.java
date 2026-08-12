package com.lexpro.lexprobackend.report.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.report.domain.CaseCardField;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CaseCardFieldResponse(
        Long fieldId,
        String fieldCode,
        String fieldName,
        JsonNode value,
        String sourceText,
        Long sourceFileId,
        JsonNode sourceLocation,
        BigDecimal confidence,
        String confirmStatus,
        Long confirmedBy,
        OffsetDateTime confirmedAt
) {
    public static CaseCardFieldResponse from(CaseCardField field, ObjectMapper objectMapper) {
        JsonNode value = field.getFieldValueJson() == null
                ? objectMapper.getNodeFactory().textNode(field.getFieldValue())
                : read(objectMapper, field.getFieldValueJson());
        return new CaseCardFieldResponse(field.getFieldId(), field.getFieldCode(), field.getFieldName(), value,
                field.getSourceText(), field.getSourceFileId(), read(objectMapper, field.getSourceLocationJson()),
                field.getConfidence(), field.getConfirmStatus(), field.getConfirmedBy(), field.getConfirmedAt());
    }

    private static JsonNode read(ObjectMapper objectMapper, String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored case-card JSON is invalid", exception);
        }
    }
}
