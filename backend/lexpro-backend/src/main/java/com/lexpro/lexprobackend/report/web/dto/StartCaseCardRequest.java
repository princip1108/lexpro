package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StartCaseCardRequest(
        @NotBlank @Pattern(regexp = "AUTO|HYBRID") String fillMode,
        @NotEmpty @Size(max = 20) List<@Valid CaseCardSourceRequest> sources
) {
}
