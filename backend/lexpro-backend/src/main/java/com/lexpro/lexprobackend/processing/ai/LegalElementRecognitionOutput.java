package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record LegalElementRecognitionOutput(
        JsonNode rawElements,
        JsonNode validationReport,
        String caseCause,
        String responseModel,
        String promptVersion,
        String schemaVersion,
        String promptSnapshot,
        JsonNode generationParameters,
        JsonNode tokenUsage
) {
}
