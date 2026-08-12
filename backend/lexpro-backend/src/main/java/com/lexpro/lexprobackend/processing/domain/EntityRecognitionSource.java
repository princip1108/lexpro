package com.lexpro.lexprobackend.processing.domain;

public record EntityRecognitionSource(
        Long docId,
        Long caseId,
        Long dossierId,
        String parseStatus,
        String rawText
) {
}
