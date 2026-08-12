package com.lexpro.lexprobackend.report.domain;

public record ReportSourceMaterial(
        String sourceType,
        Long sourceId,
        String label,
        String content,
        Boolean ready
) {
}
