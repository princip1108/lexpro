package com.lexpro.lexprobackend.processing.service;

import java.util.List;

public record CaseSummaryRequestedEvent(
        long userId,
        long caseId,
        String summaryType,
        List<Long> sourceDocIds,
        String requestId
) {
    public CaseSummaryRequestedEvent {
        sourceDocIds = List.copyOf(sourceDocIds);
    }
}
