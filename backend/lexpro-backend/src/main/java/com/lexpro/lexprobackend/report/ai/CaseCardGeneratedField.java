package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record CaseCardGeneratedField(
        String fieldCode,
        String fieldName,
        JsonNode value,
        String sourceType,
        long sourceId,
        String sourceText,
        JsonNode sourceLocation,
        BigDecimal confidence
) {
}
