package com.lexpro.lexprobackend.processing.domain;

public record LegalElementSource(
        Long docId,
        Long caseId,
        Long dossierId,
        String parseStatus,
        String rawText,
        String caseCause
) {
}
