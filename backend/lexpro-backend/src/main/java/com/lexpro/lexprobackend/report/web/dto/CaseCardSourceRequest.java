package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CaseCardSourceRequest(
        @NotBlank
        @Pattern(regexp = "DOCUMENT|ENTITY|LEGAL_ELEMENT|SUMMARY")
        String sourceType,
        @Positive long sourceId
) {
}
