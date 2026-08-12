package com.lexpro.lexprobackend.processing.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ConfirmEntityRecognitionRequest(
        @NotNull JsonNode finalEntities
) {
}
