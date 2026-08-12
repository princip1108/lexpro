package com.lexpro.lexprobackend.report.domain;

public record CaseCardSourceMaterial(
        String sourceType,
        Long sourceId,
        Long dossierId,
        String content,
        Boolean ready
) {
}
