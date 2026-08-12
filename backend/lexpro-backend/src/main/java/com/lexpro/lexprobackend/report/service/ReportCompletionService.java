package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.report.ai.ReportGenerationOutput;
import com.lexpro.lexprobackend.report.domain.CaseReport;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class ReportCompletionService {

    private final CaseReportMapper mapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ReportCompletionService(CaseReportMapper mapper, AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void succeed(ReportRequestedEvent event, ReportGenerationOutput output, int durationMs) {
        if (mapper.lockCase(event.caseId()) == null) {
            throw new IllegalStateException("Case disappeared before report completion");
        }
        CaseReport report = new CaseReport();
        report.setCaseId(event.caseId());
        report.setReportId(event.reportId());
        report.setReportContentJson(writeJson(output.reportContent()));
        report.setModelVersion(output.responseModel());
        report.setPromptSnapshot(output.promptSnapshot());
        report.setGenerationParametersJson(writeJson(output.generationParameters()));
        report.setTokenUsageJson(writeNullableJson(output.tokenUsage()));
        report.setDurationMs(durationMs);
        mapper.clearCurrent(event.caseId(), event.reportType());
        if (mapper.complete(report) == 0) {
            throw new IllegalStateException("Report is no longer generating");
        }
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_REPORT_SUCCEEDED",
                "CASE_REPORT", String.valueOf(event.reportId()), AuditResult.SUCCESS,
                Map.of("reportType", event.reportType(), "durationMs", durationMs)), event.requestId());
    }

    @Transactional
    public void fail(ReportRequestedEvent event, String errorCode, int durationMs) {
        mapper.fail(event.caseId(), event.reportId(), errorCode, durationMs);
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_REPORT_FAILED",
                "CASE_REPORT", String.valueOf(event.reportId()), AuditResult.FAILED,
                Map.of("reportType", event.reportType(), "durationMs", durationMs, "errorCode", errorCode)),
                event.requestId());
    }

    @Transactional
    public void reject(ReportRequestedEvent event) {
        fail(event, "PROCESSING_QUEUE_FULL", 0);
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Report provenance is not JSON serializable", exception);
        }
    }

    private String writeNullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : writeJson(value);
    }
}
