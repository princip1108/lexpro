package com.lexpro.lexprobackend.report.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReportTemplateRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,99}") String templateCode,
        @NotBlank @Size(max = 255) String templateName,
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String templateType,
        @NotNull JsonNode content,
        @NotBlank @Size(max = 50) String schemaVersion
) {
}
