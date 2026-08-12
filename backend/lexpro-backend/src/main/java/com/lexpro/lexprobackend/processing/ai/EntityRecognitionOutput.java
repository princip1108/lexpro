package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record EntityRecognitionOutput(
        JsonNode entities,
        String responseModel,
        String promptVersion,
        String schemaVersion,
        String promptSnapshot,
        JsonNode generationParameters,
        JsonNode tokenUsage
) {
}
