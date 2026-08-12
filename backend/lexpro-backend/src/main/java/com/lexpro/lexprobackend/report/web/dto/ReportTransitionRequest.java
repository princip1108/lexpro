package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ReportTransitionRequest(@PositiveOrZero int lockVersion) {
}
