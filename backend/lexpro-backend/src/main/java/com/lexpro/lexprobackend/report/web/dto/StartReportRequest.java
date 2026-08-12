package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StartReportRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,49}") String reportType,
        @NotBlank @Size(max = 255) String reportTitle,
        @NotNull @Positive Long templateId,
        @Positive Long cardFillTaskId,
        @Size(max = 50) List<@Valid ReportEvidenceRequest> evidence,
        @Size(max = 50) List<@Positive Long> legalElementResultIds,
        @Size(max = 50) List<@Valid ReportTypicalCaseRequest> typicalCases
) {
}
