package com.lexpro.lexprobackend.report.service;

import com.lexpro.lexprobackend.report.web.dto.ReportEvidenceRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTypicalCaseRequest;

import java.util.List;

public record ReportRequestedEvent(
        long userId,
        long caseId,
        long reportId,
        long templateId,
        String reportType,
        Long cardFillTaskId,
        List<ReportEvidenceRequest> evidence,
        List<Long> legalElementResultIds,
        List<ReportTypicalCaseRequest> typicalCases,
        String requestId
) {
    public ReportRequestedEvent {
        evidence = List.copyOf(evidence);
        legalElementResultIds = List.copyOf(legalElementResultIds);
        typicalCases = List.copyOf(typicalCases);
    }
}
