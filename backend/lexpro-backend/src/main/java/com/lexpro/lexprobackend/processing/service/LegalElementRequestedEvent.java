package com.lexpro.lexprobackend.processing.service;

public record LegalElementRequestedEvent(
        long userId,
        long caseId,
        long docId,
        String requestId
) {
}
