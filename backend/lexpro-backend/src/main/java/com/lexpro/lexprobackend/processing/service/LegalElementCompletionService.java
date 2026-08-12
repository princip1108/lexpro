package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.LegalElementResult;
import com.lexpro.lexprobackend.processing.mapper.LegalElementMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class LegalElementCompletionService {

    private final LegalElementMapper mapper;
    private final AiProcessingProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public LegalElementCompletionService(LegalElementMapper mapper, AiProcessingProperties properties,
                                         AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void succeed(LegalElementRequestedEvent event, LegalElementRecognitionOutput output, int durationMs) {
        LegalElementResult result = new LegalElementResult();
        result.setDocId(event.docId());
        result.setCaseId(event.caseId());
        result.setCaseCause(output.caseCause());
        result.setRawElementsJson(writeJson(output.rawElements()));
        result.setValidationReportJson(writeJson(output.validationReport()));
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
        mapper.insert(result);
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "LEGAL_ELEMENTS_SUCCEEDED",
                "LEGAL_ELEMENT_RESULT", String.valueOf(result.getElementResultId()), AuditResult.SUCCESS,
                Map.of("docId", event.docId(), "durationMs", durationMs,
                        "elementResultId", result.getElementResultId(), "model", properties.getModel())),
                event.requestId());
    }

    @Transactional
    public void fail(LegalElementRequestedEvent event, String errorCode, int durationMs) {
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "LEGAL_ELEMENTS_FAILED",
                "LEGAL_ELEMENT_RESULT", String.valueOf(event.docId()), AuditResult.FAILED,
                Map.of("docId", event.docId(), "durationMs", durationMs, "errorCode", errorCode)),
                event.requestId());
    }

    @Transactional
    public void reject(LegalElementRequestedEvent event) {
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "LEGAL_ELEMENTS_REJECTED",
                "LEGAL_ELEMENT_RESULT", String.valueOf(event.docId()), AuditResult.FAILED,
                Map.of("docId", event.docId(), "durationMs", 0, "errorCode", "PROCESSING_QUEUE_FULL")),
                event.requestId());
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Legal-element result is not JSON serializable", exception);
        }
    }

    private String writeNullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : writeJson(value);
    }
}
