package com.lexpro.lexprobackend.report.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record CaseReportDetailResponse(
        CaseReportSummaryResponse report,
        JsonNode content,
        List<ReportEvidenceRequest> evidence,
        List<Long> legalElementResultIds,
        List<ReportTypicalCaseResponse> typicalCases
) {
}
