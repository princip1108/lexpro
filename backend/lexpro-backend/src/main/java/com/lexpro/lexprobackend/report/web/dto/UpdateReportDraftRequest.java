package com.lexpro.lexprobackend.report.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateReportDraftRequest(
        @NotBlank @Size(max = 255) String reportTitle,
        @NotNull JsonNode content,
        @PositiveOrZero int lockVersion
) {
}
