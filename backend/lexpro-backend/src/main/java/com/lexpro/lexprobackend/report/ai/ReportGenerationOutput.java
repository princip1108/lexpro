package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;

public record ReportGenerationOutput(
        JsonNode reportContent,
        String responseModel,
        String promptVersion,
        String promptSnapshot,
        JsonNode generationParameters,
        JsonNode tokenUsage
) {
}
