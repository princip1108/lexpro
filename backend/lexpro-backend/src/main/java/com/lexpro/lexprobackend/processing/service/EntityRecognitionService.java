package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.config.AiServiceProperties;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionJobRecord;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionResult;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionSource;
import com.lexpro.lexprobackend.processing.mapper.EntityRecognitionMapper;
import com.lexpro.lexprobackend.processing.web.dto.ConfirmEntityRecognitionRequest;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionResultResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EntityRecognitionService {

    private static final int MAX_CONFIRMED_JSON_LENGTH = 262_144;
    private static final Set<String> ENTITY_TYPES = Set.of(
            "SUSPECT", "LOCATION", "ORGANIZATION", "TIME", "CRIME", "DRUG");
    private final EntityRecognitionMapper mapper;
    private final CaseAccessService caseAccessService;
    private final AiProcessingProperties properties;
    private final AiServiceProperties aiServiceProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EntityRecognitionService(EntityRecognitionMapper mapper, CaseAccessService caseAccessService,
                                    AiProcessingProperties properties, AiServiceProperties aiServiceProperties,
                                    ApplicationEventPublisher eventPublisher,
                                    AuditService auditService, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.caseAccessService = caseAccessService;
        this.properties = properties;
        this.aiServiceProperties = aiServiceProperties;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EntityRecognitionJobResponse start(long userId, long caseId, long docId, String httpRequestId) {
        caseAccessService.requireEdit(caseId, userId);
        requireProviderAvailable();
        EntityRecognitionSource source = requireSource(mapper.lockSource(caseId, docId));
        requireRecognizable(source);

        String requestId = UUID.randomUUID().toString();
        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("docId", docId);
        detail.put("dossierId", source.dossierId());
        detail.put("model", aiServiceProperties.isEnabled() ? "LexPro_8B" : properties.getModel());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            detail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, caseId, "ENTITY_RECOGNITION_STARTED", "ENTITY_RESULT",
                String.valueOf(docId), AuditResult.SUCCESS, detail), requestId);
        eventPublisher.publishEvent(new EntityRecognitionRequestedEvent(userId, caseId, docId, requestId));
        return new EntityRecognitionJobResponse(requestId, docId, "PROCESSING", null, null, requestedAt, null);
    }

    @Transactional(readOnly = true)
    public EntityRecognitionJobResponse job(long userId, long caseId, long docId, String requestId) {
        caseAccessService.requireRead(caseId, userId);
        requireSource(mapper.selectSource(caseId, docId));
        validateRequestId(requestId);
        EntityRecognitionJobRecord job = mapper.selectJob(caseId, requestId);
        if (job == null || job.getDocId() == null || job.getDocId() != docId) {
            throw notFound("Entity-recognition job not found", "ENTITY_RECOGNITION_JOB_NOT_FOUND");
        }
        return EntityRecognitionJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<EntityRecognitionResultResponse> history(long userId, long caseId, long docId) {
        caseAccessService.requireRead(caseId, userId);
        requireSource(mapper.selectSource(caseId, docId));
        return mapper.selectHistory(caseId, docId).stream()
                .map(result -> EntityRecognitionResultResponse.from(result, objectMapper))
                .toList();
    }

    @Transactional(readOnly = true)
    public EntityRecognitionResultResponse detail(long userId, long caseId, long docId, long entityResultId) {
        caseAccessService.requireRead(caseId, userId);
        requireSource(mapper.selectSource(caseId, docId));
        return EntityRecognitionResultResponse.from(requireResult(caseId, docId, entityResultId), objectMapper);
    }

    @Transactional
    public EntityRecognitionResultResponse confirm(long userId, long caseId, long docId, long entityResultId,
                                                    ConfirmEntityRecognitionRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        EntityRecognitionSource source = requireSource(mapper.selectSource(caseId, docId));
        String finalEntitiesJson = validateAndSerialize(request.finalEntities(), source.rawText());
        if (mapper.confirm(caseId, docId, entityResultId, finalEntitiesJson, userId) == 0) {
            EntityRecognitionResult existing = mapper.selectDetail(caseId, docId, entityResultId);
            if (existing == null) {
                throw notFound("Entity-recognition result not found", "ENTITY_RESULT_NOT_FOUND");
            }
            throw new ApiException(HttpStatus.CONFLICT, "Entity result already confirmed",
                    "ENTITY_RESULT_ALREADY_CONFIRMED", "A confirmed entity result cannot be overwritten");
        }
        auditService.record(new AuditEvent(userId, caseId, "ENTITY_RESULT_CONFIRMED", "ENTITY_RESULT",
                String.valueOf(entityResultId), AuditResult.SUCCESS, Map.of("docId", docId)));
        return EntityRecognitionResultResponse.from(requireResult(caseId, docId, entityResultId), objectMapper);
    }

    private void requireProviderAvailable() {
        if (aiServiceProperties.isEnabled()) {
            return;
        }
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI processing disabled", "AI_PROVIDER_DISABLED",
                    "AI processing is not enabled for this environment");
        }
        if (!properties.isAllowExternalCaseData()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "External AI data export disabled",
                    "AI_DATA_EXPORT_DISABLED",
                    "External case-data export must be explicitly approved before AI processing can start");
        }
    }

    private EntityRecognitionSource requireSource(EntityRecognitionSource source) {
        if (source == null) {
            throw notFound("Parse result not found", "PARSE_RESULT_NOT_FOUND");
        }
        return source;
    }

    private void requireRecognizable(EntityRecognitionSource source) {
        if (!"SUCCESS".equals(source.parseStatus()) || source.rawText() == null || source.rawText().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "Document text unavailable", "ENTITY_SOURCE_UNAVAILABLE",
                    "A successful parse result with extracted text is required");
        }
        if (source.rawText().length() > properties.getMaxInputChars()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AI input too large", "AI_INPUT_TOO_LARGE",
                    "Extracted document text exceeds the configured AI input limit");
        }
    }

    private EntityRecognitionResult requireResult(long caseId, long docId, long entityResultId) {
        EntityRecognitionResult result = mapper.selectDetail(caseId, docId, entityResultId);
        if (result == null) {
            throw notFound("Entity-recognition result not found", "ENTITY_RESULT_NOT_FOUND");
        }
        return result;
    }

    private String validateAndSerialize(JsonNode root, String sourceText) {
        validateEntities(root, sourceText);
        try {
            String json = objectMapper.writeValueAsString(root);
            if (json.length() > MAX_CONFIRMED_JSON_LENGTH) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Confirmed entities too large",
                        "ENTITY_RESULT_TOO_LARGE", "Confirmed entity JSON exceeds the allowed size");
            }
            return json;
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid entity JSON", "ENTITY_RESULT_INVALID",
                    "Confirmed entities must be valid JSON");
        }
    }

    private void validateEntities(JsonNode root, String sourceText) {
        JsonNode entities = root == null ? null : root.get("entities");
        if (root == null || !root.isObject() || entities == null || !entities.isArray()
                || entities.size() > 1_000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid entity JSON", "ENTITY_RESULT_INVALID",
                    "Confirmed entities must be an object containing an entities array");
        }
        for (JsonNode entity : entities) {
            if (!entity.isObject() || !entity.path("type").isTextual() || entity.path("type").textValue().isBlank()
                    || !entity.path("text").isTextual() || entity.path("text").textValue().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid entity JSON", "ENTITY_RESULT_INVALID",
                        "Each confirmed entity must contain non-blank type and text fields");
            }
            String type = entity.path("type").textValue();
            String entityText = entity.path("text").textValue();
            if (!ENTITY_TYPES.contains(type) || sourceText == null || !sourceText.contains(entityText)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid entity JSON", "ENTITY_RESULT_INVALID",
                        "Each confirmed entity must use an allowed type and exact source text");
            }
            validateOffsets(entity, sourceText, entityText);
        }
    }

    private void validateOffsets(JsonNode entity, String sourceText, String entityText) {
        JsonNode startNode = entity.has("globalStartUtf16")
                ? entity.get("globalStartUtf16") : entity.get("startOffset");
        JsonNode endNode = entity.has("globalEndUtf16")
                ? entity.get("globalEndUtf16") : entity.get("endOffset");
        if (startNode == null && endNode == null) {
            return;
        }
        if (startNode == null || endNode == null || !startNode.canConvertToInt() || !endNode.canConvertToInt()) {
            throw invalidOffsets();
        }
        int start = startNode.intValue();
        int end = endNode.intValue();
        if (start < 0 || end < start || end > sourceText.length()
                || !sourceText.substring(start, end).equals(entityText)) {
            throw invalidOffsets();
        }
    }

    private ApiException invalidOffsets() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Invalid entity JSON", "ENTITY_RESULT_INVALID",
                "Entity offsets must exactly identify the supplied source text");
    }

    private void validateRequestId(String requestId) {
        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid AI request ID", "AI_REQUEST_ID_INVALID",
                    "The AI request ID must be a UUID");
        }
    }

    private ApiException notFound(String title, String errorCode) {
        return new ApiException(HttpStatus.NOT_FOUND, title, errorCode, "The requested resource does not exist");
    }
}
