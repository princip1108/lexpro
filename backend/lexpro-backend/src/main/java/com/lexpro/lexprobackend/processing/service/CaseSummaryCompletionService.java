package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryResult;
import com.lexpro.lexprobackend.processing.mapper.CaseSummaryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class CaseSummaryCompletionService {

    private final CaseSummaryMapper mapper;
    private final AiProcessingProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CaseSummaryCompletionService(CaseSummaryMapper mapper, AiProcessingProperties properties,
                                        AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void succeed(CaseSummaryRequestedEvent event, CaseSummaryOutput output, int durationMs) {
        if (mapper.lockCase(event.caseId()) == null) {
            throw new IllegalStateException("Case disappeared before summary completion");
        }
        CaseSummaryResult result = new CaseSummaryResult();
        result.setCaseId(event.caseId());
        result.setSummaryType(event.summaryType());
        result.setSummaryText(output.summaryText());
        result.setVersionNo(mapper.selectNextVersion(event.caseId(), event.summaryType()));
        result.setModelName(properties.getModel());
        result.setModelVersion(output.responseModel());
        result.setPromptVersion(output.promptVersion());
        result.setSchemaVersion(output.schemaVersion());
        result.setPromptSnapshot(output.promptSnapshot());
        result.setGenerationParametersJson(writeJson(output.generationParameters()));
        result.setTokenUsageJson(writeNullableJson(output.tokenUsage()));
        result.setRequestId(event.requestId());
        result.setDurationMs(durationMs);
        result.setCreatedBy(event.userId());
        mapper.clearCurrent(event.caseId(), event.summaryType());
        mapper.insert(result);
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_SUMMARY_SUCCEEDED",
                "CASE_SUMMARY", String.valueOf(result.getSummaryId()), AuditResult.SUCCESS,
                Map.of("summaryType", event.summaryType(), "versionNo", result.getVersionNo(),
                        "summaryId", result.getSummaryId(), "durationMs", durationMs,
                        "model", properties.getModel())), event.requestId());
    }

    @Transactional
    public void fail(CaseSummaryRequestedEvent event, String errorCode, int durationMs) {
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_SUMMARY_FAILED",
                "CASE_SUMMARY", String.valueOf(event.caseId()), AuditResult.FAILED,
                Map.of("summaryType", event.summaryType(), "durationMs", durationMs, "errorCode", errorCode)),
                event.requestId());
    }

    @Transactional
    public void reject(CaseSummaryRequestedEvent event) {
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_SUMMARY_REJECTED",
                "CASE_SUMMARY", String.valueOf(event.caseId()), AuditResult.FAILED,
                Map.of("summaryType", event.summaryType(), "durationMs", 0,
                        "errorCode", "PROCESSING_QUEUE_FULL")), event.requestId());
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Case-summary provenance is not JSON serializable", exception);
        }
    }

    private String writeNullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : writeJson(value);
    }
}
