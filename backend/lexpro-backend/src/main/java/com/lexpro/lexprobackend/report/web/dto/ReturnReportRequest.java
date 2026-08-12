package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReturnReportRequest(
        @PositiveOrZero int lockVersion,
        @NotBlank @Size(max = 2000) String reason
) {
}
