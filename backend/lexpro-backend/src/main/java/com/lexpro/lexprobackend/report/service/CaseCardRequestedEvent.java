package com.lexpro.lexprobackend.report.service;

import com.lexpro.lexprobackend.report.web.dto.CaseCardSourceRequest;

import java.util.List;

public record CaseCardRequestedEvent(
        long userId,
        long caseId,
        long fillTaskId,
        String fillMode,
        List<CaseCardSourceRequest> sources,
        String requestId
) {
    public CaseCardRequestedEvent {
        sources = List.copyOf(sources);
    }
}
