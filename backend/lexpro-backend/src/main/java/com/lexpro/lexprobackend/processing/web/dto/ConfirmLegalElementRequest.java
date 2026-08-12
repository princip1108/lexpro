package com.lexpro.lexprobackend.processing.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record ConfirmLegalElementRequest(
        @NotNull JsonNode finalElements
) {
}
