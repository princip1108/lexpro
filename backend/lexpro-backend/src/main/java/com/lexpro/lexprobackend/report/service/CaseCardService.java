package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.ai.OpenAiCompatibleCaseCardGenerationClient;
import com.lexpro.lexprobackend.report.domain.CaseCardField;
import com.lexpro.lexprobackend.report.domain.CaseCardFillTask;
import com.lexpro.lexprobackend.report.domain.CaseCardJobRecord;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import com.lexpro.lexprobackend.report.mapper.CaseCardMapper;
import com.lexpro.lexprobackend.report.web.dto.CaseCardDetailResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardFieldResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardJobResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardSourceRequest;
import com.lexpro.lexprobackend.report.web.dto.CaseCardSourceResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardSummaryResponse;
import com.lexpro.lexprobackend.report.web.dto.ConfirmCaseCardFieldRequest;
import com.lexpro.lexprobackend.report.web.dto.StartCaseCardRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CaseCardService {

    private final CaseCardMapper mapper;
    private final CaseAccessService caseAccessService;
    private final AiProcessingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CaseCardService(CaseCardMapper mapper, CaseAccessService caseAccessService,
                           AiProcessingProperties properties, ApplicationEventPublisher eventPublisher,
                           AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.caseAccessService = caseAccessService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CaseCardJobResponse start(long userId, long caseId, StartCaseCardRequest request,
                                     String httpRequestId) {
        caseAccessService.requireEdit(caseId, userId);
        requireProviderAvailable();
        List<CaseCardSourceRequest> sources = validateSources(caseId, request.sources());
        if (mapper.lockCase(caseId) == null) {
            throw notFound("Case not found", "CASE_NOT_FOUND");
        }

        String requestId = UUID.randomUUID().toString();
        CaseCardFillTask task = new CaseCardFillTask();
        task.setCaseId(caseId);
        task.setFillMode(request.fillMode());
        task.setOperatorId(userId);
        task.setModelName(properties.getModel());
        task.setPromptVersion(OpenAiCompatibleCaseCardGenerationClient.PROMPT_VERSION);
        task.setRequestId(requestId);
        mapper.insertTask(task);
        sources.forEach(source -> mapper.insertSource(task.getFillTaskId(), caseId,
                source.sourceType(), source.sourceId()));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fillTaskId", task.getFillTaskId());
        detail.put("fillMode", request.fillMode());
        detail.put("sourceCount", sources.size());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            detail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_CARD_STARTED", "CASE_CARD",
                String.valueOf(task.getFillTaskId()), AuditResult.SUCCESS, detail), requestId);
        eventPublisher.publishEvent(new CaseCardRequestedEvent(userId, caseId, task.getFillTaskId(),
                request.fillMode(), sources, requestId));
        return CaseCardJobResponse.from(mapper.selectJob(caseId, requestId));
    }

    @Transactional(readOnly = true)
    public CaseCardJobResponse job(long userId, long caseId, String requestId) {
        caseAccessService.requireRead(caseId, userId);
        validateRequestId(requestId);
        CaseCardJobRecord job = mapper.selectJob(caseId, requestId);
        if (job == null) {
            throw notFound("Case-card job not found", "CASE_CARD_JOB_NOT_FOUND");
        }
        return CaseCardJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<CaseCardSummaryResponse> history(long userId, long caseId) {
        caseAccessService.requireRead(caseId, userId);
        return mapper.selectHistory(caseId).stream().map(CaseCardSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CaseCardDetailResponse detail(long userId, long caseId, long fillTaskId) {
        caseAccessService.requireRead(caseId, userId);
        return detail(caseId, fillTaskId);
    }

    @Transactional
    public CaseCardFieldResponse confirmField(long userId, long caseId, long fillTaskId, long fieldId,
                                              ConfirmCaseCardFieldRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        requireDraftTask(caseId, fillTaskId);
        CaseCardField field = mapper.selectField(caseId, fillTaskId, fieldId);
        if (field == null) {
            throw notFound("Case-card field not found", "CASE_CARD_FIELD_NOT_FOUND");
        }
        if (!"UNCONFIRMED".equals(field.getConfirmStatus())) {
            throw conflict("Case-card field already resolved", "CASE_CARD_FIELD_ALREADY_RESOLVED");
        }
        if ("REJECTED".equals(request.status()) && request.value() != null) {
            throw badRequest("Rejected fields cannot contain a replacement value", "CASE_CARD_REJECTED_VALUE");
        }
        if (request.value() != null) {
            applyValue(field, request.value());
        }
        field.setConfirmStatus(request.status());
        field.setConfirmedBy(userId);
        if (mapper.confirmField(caseId, field) == 0) {
            throw conflict("Case-card field already resolved", "CASE_CARD_FIELD_ALREADY_RESOLVED");
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_CARD_FIELD_" + request.status(),
                "CASE_CARD_FIELD", String.valueOf(fieldId), AuditResult.SUCCESS,
                Map.of("fillTaskId", fillTaskId)));
        return CaseCardFieldResponse.from(mapper.selectField(caseId, fillTaskId, fieldId), objectMapper);
    }

    @Transactional
    public CaseCardDetailResponse confirmTask(long userId, long caseId, long fillTaskId) {
        caseAccessService.requireEdit(caseId, userId);
        requireDraftTask(caseId, fillTaskId);
        List<CaseCardField> fields = mapper.selectFields(fillTaskId);
        if (fields.isEmpty()) {
            throw conflict("Case card has no fields", "CASE_CARD_FIELDS_EMPTY");
        }
        if (mapper.countUnconfirmedFields(fillTaskId) > 0) {
            throw conflict("Resolve every case-card field before confirmation", "CASE_CARD_FIELDS_UNRESOLVED");
        }
        if (mapper.confirmTask(caseId, fillTaskId, userId) == 0) {
            throw conflict("Case card is no longer a draft", "CASE_CARD_NOT_DRAFT");
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_CARD_CONFIRMED", "CASE_CARD",
                String.valueOf(fillTaskId), AuditResult.SUCCESS, Map.of("fieldCount", fields.size())));
        return detail(caseId, fillTaskId);
    }

    private List<CaseCardSourceRequest> validateSources(long caseId, List<CaseCardSourceRequest> sources) {
        Set<String> keys = new HashSet<>();
        for (CaseCardSourceRequest source : sources) {
            String key = source.sourceType() + ':' + source.sourceId();
            if (!keys.add(key)) {
                throw badRequest("Case-card sources must be unique", "CASE_CARD_SOURCES_DUPLICATE");
            }
        }
        for (CaseCardSourceRequest source : sources) {
            CaseCardSourceMaterial material = mapper.selectSource(caseId, source.sourceType(), source.sourceId());
            if (material == null) {
                throw badRequest("A selected case-card source does not belong to the case",
                        "CASE_CARD_SOURCE_NOT_FOUND");
            }
            if (!Boolean.TRUE.equals(material.ready())) {
                throw conflict("A selected case-card source is not ready", "CASE_CARD_SOURCE_NOT_READY");
            }
        }
        return List.copyOf(sources);
    }

    private CaseCardDetailResponse detail(long caseId, long fillTaskId) {
        CaseCardFillTask task = mapper.selectTask(caseId, fillTaskId);
        if (task == null) {
            throw notFound("Case card not found", "CASE_CARD_NOT_FOUND");
        }
        return new CaseCardDetailResponse(CaseCardSummaryResponse.from(task),
                mapper.selectTaskSources(caseId, fillTaskId).stream().map(CaseCardSourceResponse::from).toList(),
                mapper.selectFields(fillTaskId).stream()
                        .map(field -> CaseCardFieldResponse.from(field, objectMapper)).toList());
    }

    private void requireDraftTask(long caseId, long fillTaskId) {
        CaseCardFillTask task = mapper.selectTask(caseId, fillTaskId);
        if (task == null) {
            throw notFound("Case card not found", "CASE_CARD_NOT_FOUND");
        }
        if (!"DRAFT".equals(task.getFillStatus())) {
            throw conflict("Only a draft case card can be changed", "CASE_CARD_NOT_DRAFT");
        }
    }

    private void applyValue(CaseCardField field, JsonNode value) {
        if (value.isNull() || (value.isTextual() && value.textValue().isBlank())) {
            throw badRequest("Confirmed field value cannot be blank", "CASE_CARD_FIELD_VALUE_INVALID");
        }
        if (value.isTextual()) {
            field.setFieldValue(value.textValue());
            field.setFieldValueJson(null);
        } else {
            field.setFieldValue(null);
            try {
                field.setFieldValueJson(objectMapper.writeValueAsString(value));
            } catch (JsonProcessingException exception) {
                throw badRequest("Confirmed field value is not valid JSON", "CASE_CARD_FIELD_VALUE_INVALID");
            }
        }
    }

    private void requireProviderAvailable() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI processing disabled", "AI_PROVIDER_DISABLED",
                    "AI processing is not enabled for this environment");
        }
        if (!properties.isAllowExternalCaseData()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "External case-data transfer disabled",
                    "AI_DATA_EXPORT_DISABLED", "External case-data transfer is not enabled for this environment");
        }
    }

    private void validateRequestId(String requestId) {
        try {
            UUID.fromString(requestId);
        } catch (RuntimeException exception) {
            throw badRequest("Request ID must be a UUID", "INVALID_REQUEST_ID");
        }
    }

    private ApiException notFound(String title, String code) {
        return new ApiException(HttpStatus.NOT_FOUND, title, code, title);
    }

    private ApiException badRequest(String detail, String code) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid case-card request", code, detail);
    }

    private ApiException conflict(String detail, String code) {
        return new ApiException(HttpStatus.CONFLICT, "Case-card conflict", code, detail);
    }
}
