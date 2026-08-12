package com.lexpro.lexprobackend.report.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmCaseCardFieldRequest(
        @NotBlank @Pattern(regexp = "CONFIRMED|REJECTED") String status,
        JsonNode value
) {
}
