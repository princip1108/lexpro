package com.lexpro.lexprobackend.processing.service;

public record EntityRecognitionRequestedEvent(
        long userId,
        long caseId,
        long docId,
        String requestId
) {
}
