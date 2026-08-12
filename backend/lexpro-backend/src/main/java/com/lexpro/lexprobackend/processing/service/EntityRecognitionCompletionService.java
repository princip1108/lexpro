package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionResult;
import com.lexpro.lexprobackend.processing.mapper.EntityRecognitionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class EntityRecognitionCompletionService {

    private final EntityRecognitionMapper mapper;
    private final AiProcessingProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EntityRecognitionCompletionService(EntityRecognitionMapper mapper, AiProcessingProperties properties,
                                              AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void succeed(EntityRecognitionRequestedEvent event, EntityRecognitionOutput output, int durationMs) {
        EntityRecognitionResult result = new EntityRecognitionResult();
        result.setDocId(event.docId());
        result.setCaseId(event.caseId());
        result.setEntitiesJson(writeJson(output.entities()));
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
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "ENTITY_RECOGNITION_SUCCEEDED",
                "ENTITY_RESULT", String.valueOf(result.getEntityResultId()), AuditResult.SUCCESS,
                Map.of("docId", event.docId(), "durationMs", durationMs,
                        "entityResultId", result.getEntityResultId(), "model", properties.getModel())),
                event.requestId());
    }

    @Transactional
    public void fail(EntityRecognitionRequestedEvent event, String errorCode, int durationMs) {
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "ENTITY_RECOGNITION_FAILED",
                "ENTITY_RESULT", String.valueOf(event.docId()), AuditResult.FAILED,
                Map.of("docId", event.docId(), "durationMs", durationMs, "errorCode", errorCode)),
                event.requestId());
    }

    @Transactional
    public void reject(EntityRecognitionRequestedEvent event) {
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "ENTITY_RECOGNITION_REJECTED",
                "ENTITY_RESULT", String.valueOf(event.docId()), AuditResult.FAILED,
                Map.of("docId", event.docId(), "durationMs", 0, "errorCode", "PROCESSING_QUEUE_FULL")),
                event.requestId());
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Entity-recognition result is not JSON serializable", exception);
        }
    }

    private String writeNullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : writeJson(value);
    }
}
