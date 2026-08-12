package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record CaseCardGenerationOutput(
        List<CaseCardGeneratedField> fields,
        String responseModel,
        String promptVersion,
        String promptSnapshot,
        JsonNode generationParameters,
        JsonNode tokenUsage
) {
}
