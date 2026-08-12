package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.ai.LegalElementJsonValidator;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.LegalElementJobRecord;
import com.lexpro.lexprobackend.processing.domain.LegalElementResult;
import com.lexpro.lexprobackend.processing.domain.LegalElementSource;
import com.lexpro.lexprobackend.processing.mapper.LegalElementMapper;
import com.lexpro.lexprobackend.processing.web.dto.ConfirmLegalElementRequest;
import com.lexpro.lexprobackend.processing.web.dto.LegalElementJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.LegalElementResultResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class LegalElementService {

    private static final int MAX_CONFIRMED_JSON_LENGTH = 524_288;
    private final LegalElementMapper mapper;
    private final CaseAccessService caseAccessService;
    private final AiProcessingProperties properties;
    private final LegalElementJsonValidator validator;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public LegalElementService(LegalElementMapper mapper, CaseAccessService caseAccessService,
                               AiProcessingProperties properties, LegalElementJsonValidator validator,
                               ApplicationEventPublisher eventPublisher, AuditService auditService,
                               ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.caseAccessService = caseAccessService;
        this.properties = properties;
        this.validator = validator;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LegalElementJobResponse start(long userId, long caseId, long docId, String httpRequestId) {
        caseAccessService.requireEdit(caseId, userId);
        requireProviderAvailable();
        LegalElementSource source = requireSource(mapper.lockSource(caseId, docId));
        requireRecognizable(source);
        String requestId = UUID.randomUUID().toString();
        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("docId", docId);
        detail.put("dossierId", source.dossierId());
        detail.put("model", properties.getModel());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            detail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, caseId, "LEGAL_ELEMENTS_STARTED", "LEGAL_ELEMENT_RESULT",
                String.valueOf(docId), AuditResult.SUCCESS, detail), requestId);
        eventPublisher.publishEvent(new LegalElementRequestedEvent(userId, caseId, docId, requestId));
        return new LegalElementJobResponse(requestId, docId, "PROCESSING", null, null, requestedAt, null);
    }

    @Transactional(readOnly = true)
    public LegalElementJobResponse job(long userId, long caseId, long docId, String requestId) {
        caseAccessService.requireRead(caseId, userId);
        requireSource(mapper.selectSource(caseId, docId));
        validateRequestId(requestId);
        LegalElementJobRecord job = mapper.selectJob(caseId, requestId);
        if (job == null || job.getDocId() == null || job.getDocId() != docId) {
            throw notFound("Legal-element job not found", "LEGAL_ELEMENT_JOB_NOT_FOUND");
        }
        return LegalElementJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<LegalElementResultResponse> history(long userId, long caseId, long docId) {
        caseAccessService.requireRead(caseId, userId);
        requireSource(mapper.selectSource(caseId, docId));
        return mapper.selectHistory(caseId, docId).stream()
                .map(result -> LegalElementResultResponse.from(result, objectMapper)).toList();
    }

    @Transactional(readOnly = true)
    public LegalElementResultResponse detail(long userId, long caseId, long docId, long elementResultId) {
        caseAccessService.requireRead(caseId, userId);
        requireSource(mapper.selectSource(caseId, docId));
        return LegalElementResultResponse.from(requireResult(caseId, docId, elementResultId), objectMapper);
    }

    @Transactional
    public LegalElementResultResponse confirm(long userId, long caseId, long docId, long elementResultId,
                                              ConfirmLegalElementRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        LegalElementSource source = requireSource(mapper.selectSource(caseId, docId));
        String finalJson = validateAndSerialize(request.finalElements(), source.rawText());
        if (mapper.confirm(caseId, docId, elementResultId, finalJson, userId) == 0) {
            LegalElementResult existing = mapper.selectDetail(caseId, docId, elementResultId);
            if (existing == null) {
                throw notFound("Legal-element result not found", "LEGAL_ELEMENT_RESULT_NOT_FOUND");
            }
            throw new ApiException(HttpStatus.CONFLICT, "Legal-element result already confirmed",
                    "LEGAL_ELEMENT_ALREADY_CONFIRMED", "A confirmed legal-element result cannot be overwritten");
        }
        auditService.record(new AuditEvent(userId, caseId, "LEGAL_ELEMENTS_CONFIRMED", "LEGAL_ELEMENT_RESULT",
                String.valueOf(elementResultId), AuditResult.SUCCESS, Map.of("docId", docId)));
        return LegalElementResultResponse.from(requireResult(caseId, docId, elementResultId), objectMapper);
    }

    private void requireProviderAvailable() {
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

    private LegalElementSource requireSource(LegalElementSource source) {
        if (source == null) {
            throw notFound("Parse result not found", "PARSE_RESULT_NOT_FOUND");
        }
        return source;
    }

    private void requireRecognizable(LegalElementSource source) {
        if (!"SUCCESS".equals(source.parseStatus()) || source.rawText() == null || source.rawText().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "Document text unavailable",
                    "LEGAL_ELEMENT_SOURCE_UNAVAILABLE", "A successful parse result with extracted text is required");
        }
        if (source.rawText().length() > properties.getMaxInputChars()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AI input too large", "AI_INPUT_TOO_LARGE",
                    "Extracted document text exceeds the configured AI input limit");
        }
    }

    private String validateAndSerialize(JsonNode root, String sourceText) {
        try {
            validator.validate(root, sourceText);
            String json = objectMapper.writeValueAsString(root);
            if (json.length() > MAX_CONFIRMED_JSON_LENGTH) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Confirmed legal elements too large",
                        "LEGAL_ELEMENT_RESULT_TOO_LARGE", "Confirmed legal-element JSON exceeds the allowed size");
            }
            return json;
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid legal-element JSON",
                    "LEGAL_ELEMENT_RESULT_INVALID", exception.getMessage());
        }
    }

    private LegalElementResult requireResult(long caseId, long docId, long elementResultId) {
        LegalElementResult result = mapper.selectDetail(caseId, docId, elementResultId);
        if (result == null) {
            throw notFound("Legal-element result not found", "LEGAL_ELEMENT_RESULT_NOT_FOUND");
        }
        return result;
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
