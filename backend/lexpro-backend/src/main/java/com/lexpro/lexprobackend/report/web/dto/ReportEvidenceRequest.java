package com.lexpro.lexprobackend.report.web.dto;

import jakarta.validation.constraints.Positive;

public record ReportEvidenceRequest(
        @Positive long dossierId,
        @Positive Integer sortNo
) {
}
