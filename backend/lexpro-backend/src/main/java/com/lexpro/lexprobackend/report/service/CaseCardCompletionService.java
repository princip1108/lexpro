package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.report.ai.CaseCardGeneratedField;
import com.lexpro.lexprobackend.report.ai.CaseCardGenerationOutput;
import com.lexpro.lexprobackend.report.domain.CaseCardField;
import com.lexpro.lexprobackend.report.domain.CaseCardFillTask;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import com.lexpro.lexprobackend.report.mapper.CaseCardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CaseCardCompletionService {

    private final CaseCardMapper mapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CaseCardCompletionService(CaseCardMapper mapper, AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void succeed(CaseCardRequestedEvent event, List<CaseCardSourceMaterial> sources,
                        CaseCardGenerationOutput output, int durationMs) {
        Map<String, CaseCardSourceMaterial> sourceMap = sources.stream().collect(Collectors.toMap(
                source -> key(source.sourceType(), source.sourceId()), Function.identity()));
        for (CaseCardGeneratedField generated : output.fields()) {
            CaseCardSourceMaterial source = sourceMap.get(key(generated.sourceType(), generated.sourceId()));
            if (source == null) {
                throw new IllegalStateException("Generated case-card source was not selected");
            }
            mapper.insertField(toField(event.fillTaskId(), generated, source.dossierId()));
        }
        CaseCardFillTask task = new CaseCardFillTask();
        task.setCaseId(event.caseId());
        task.setFillTaskId(event.fillTaskId());
        task.setModelVersion(output.responseModel());
        task.setPromptSnapshot(output.promptSnapshot());
        task.setGenerationParametersJson(writeJson(output.generationParameters()));
        task.setTokenUsageJson(writeNullableJson(output.tokenUsage()));
        task.setDurationMs(durationMs);
        if (mapper.completeTask(task) == 0) {
            throw new IllegalStateException("Case-card task is no longer processing");
        }
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_CARD_SUCCEEDED",
                "CASE_CARD", String.valueOf(event.fillTaskId()), AuditResult.SUCCESS,
                Map.of("fillMode", event.fillMode(), "fieldCount", output.fields().size(),
                        "durationMs", durationMs)), event.requestId());
    }

    @Transactional
    public void fail(CaseCardRequestedEvent event, String errorCode, int durationMs) {
        mapper.failTask(event.caseId(), event.fillTaskId(), errorCode, durationMs);
        auditService.record(new AuditEvent(event.userId(), event.caseId(), "CASE_CARD_FAILED",
                "CASE_CARD", String.valueOf(event.fillTaskId()), AuditResult.FAILED,
                Map.of("durationMs", durationMs, "errorCode", errorCode)), event.requestId());
    }

    @Transactional
    public void reject(CaseCardRequestedEvent event) {
        fail(event, "PROCESSING_QUEUE_FULL", 0);
    }

    private CaseCardField toField(long fillTaskId, CaseCardGeneratedField generated, Long dossierId) {
        CaseCardField field = new CaseCardField();
        field.setFillTaskId(fillTaskId);
        field.setFieldCode(generated.fieldCode());
        field.setFieldName(generated.fieldName());
        if (generated.value().isTextual()) {
            field.setFieldValue(generated.value().textValue());
        } else {
            field.setFieldValueJson(writeJson(generated.value()));
        }
        field.setSourceText(generated.sourceText());
        field.setSourceFileId(dossierId);
        ObjectNode location = objectMapper.createObjectNode();
        location.put("sourceType", generated.sourceType());
        location.put("sourceId", generated.sourceId());
        if (generated.sourceLocation() != null) {
            location.set("location", generated.sourceLocation());
        }
        field.setSourceLocationJson(writeJson(location));
        field.setConfidence(generated.confidence());
        return field;
    }

    private String key(String type, long id) {
        return type + ':' + id;
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Case-card data is not JSON serializable", exception);
        }
    }

    private String writeNullableJson(JsonNode value) {
        return value == null || value.isNull() ? null : writeJson(value);
    }
}
