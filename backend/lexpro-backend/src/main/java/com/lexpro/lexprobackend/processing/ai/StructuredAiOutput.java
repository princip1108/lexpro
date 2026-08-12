package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record StructuredAiOutput(
        JsonNode content,
        String responseModel,
        JsonNode generationParameters,
        JsonNode tokenUsage
) {
}
