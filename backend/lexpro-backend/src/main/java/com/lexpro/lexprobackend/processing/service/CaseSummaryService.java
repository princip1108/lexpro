package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryJobRecord;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryResult;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import com.lexpro.lexprobackend.processing.mapper.CaseSummaryMapper;
import com.lexpro.lexprobackend.processing.web.dto.CaseSummaryJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.CaseSummaryResponse;
import com.lexpro.lexprobackend.processing.web.dto.StartCaseSummaryRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CaseSummaryService {

    private static final Set<String> SUMMARY_TYPES = Set.of("FACT", "PROCESS", "CONCLUSION", "FULL");
    private final CaseSummaryMapper mapper;
    private final CaseAccessService caseAccessService;
    private final AiProcessingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CaseSummaryService(CaseSummaryMapper mapper, CaseAccessService caseAccessService,
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
    public CaseSummaryJobResponse start(long userId, long caseId, StartCaseSummaryRequest request,
                                        String httpRequestId) {
        caseAccessService.requireEdit(caseId, userId);
        requireProviderAvailable();
        String summaryType = requireSummaryType(request.summaryType());
        List<Long> sourceDocIds = requireDistinctDocIds(request.sourceDocIds());
        if (mapper.lockCase(caseId) == null) {
            throw notFound("Case not found", "CASE_NOT_FOUND");
        }
        List<CaseSummarySource> sources = mapper.selectSources(caseId, sourceDocIds);
        validateSources(sources, sourceDocIds.size());

        String requestId = UUID.randomUUID().toString();
        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("summaryType", summaryType);
        detail.put("sourceDocIds", sourceDocIds);
        detail.put("model", properties.getModel());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            detail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_SUMMARY_STARTED", "CASE_SUMMARY",
                String.valueOf(caseId), AuditResult.SUCCESS, detail), requestId);
        eventPublisher.publishEvent(new CaseSummaryRequestedEvent(
                userId, caseId, summaryType, sourceDocIds, requestId));
        return new CaseSummaryJobResponse(requestId, summaryType, "PROCESSING", null, null, requestedAt, null);
    }

    @Transactional(readOnly = true)
    public CaseSummaryJobResponse job(long userId, long caseId, String requestId) {
        caseAccessService.requireRead(caseId, userId);
        validateRequestId(requestId);
        CaseSummaryJobRecord job = mapper.selectJob(caseId, requestId);
        if (job == null) {
            throw notFound("Case-summary job not found", "CASE_SUMMARY_JOB_NOT_FOUND");
        }
        return CaseSummaryJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<CaseSummaryResponse> history(long userId, long caseId, String summaryType) {
        caseAccessService.requireRead(caseId, userId);
        String checkedType = requireSummaryType(summaryType);
        return mapper.selectHistory(caseId, checkedType).stream()
                .map(result -> CaseSummaryResponse.from(result, objectMapper)).toList();
    }

    @Transactional(readOnly = true)
    public CaseSummaryResponse detail(long userId, long caseId, long summaryId) {
        caseAccessService.requireRead(caseId, userId);
        return CaseSummaryResponse.from(requireResult(caseId, summaryId), objectMapper);
    }

    @Transactional
    public CaseSummaryResponse confirm(long userId, long caseId, long summaryId) {
        caseAccessService.requireEdit(caseId, userId);
        if (mapper.confirm(caseId, summaryId, userId) == 0) {
            CaseSummaryResult existing = mapper.selectDetail(caseId, summaryId);
            if (existing == null) {
                throw notFound("Case summary not found", "CASE_SUMMARY_NOT_FOUND");
            }
            throw new ApiException(HttpStatus.CONFLICT, "Case summary already confirmed",
                    "CASE_SUMMARY_ALREADY_CONFIRMED", "A confirmed case summary cannot be confirmed again");
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_SUMMARY_CONFIRMED", "CASE_SUMMARY",
                String.valueOf(summaryId), AuditResult.SUCCESS, Map.of()));
        return CaseSummaryResponse.from(requireResult(caseId, summaryId), objectMapper);
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

    private String requireSummaryType(String summaryType) {
        if (summaryType == null || !SUMMARY_TYPES.contains(summaryType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid summary type", "SUMMARY_TYPE_INVALID",
                    "Summary type must be FACT, PROCESS, CONCLUSION or FULL");
        }
        return summaryType;
    }

    private List<Long> requireDistinctDocIds(List<Long> sourceDocIds) {
        if (sourceDocIds == null || sourceDocIds.isEmpty() || sourceDocIds.size() > 20
                || sourceDocIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid summary sources", "SUMMARY_SOURCES_INVALID",
                    "One to twenty positive source document IDs are required");
        }
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(sourceDocIds));
        if (distinct.size() != sourceDocIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate summary sources", "SUMMARY_SOURCES_DUPLICATE",
                    "Source document IDs must be unique");
        }
        return distinct;
    }

    private void validateSources(List<CaseSummarySource> sources, int expectedCount) {
        if (sources == null || sources.size() != expectedCount || sources.stream()
                .anyMatch(source -> !"SUCCESS".equals(source.parseStatus())
                        || source.rawText() == null || source.rawText().isBlank())) {
            throw new ApiException(HttpStatus.CONFLICT, "Summary source unavailable", "SUMMARY_SOURCE_UNAVAILABLE",
                    "Every source must be a successful parse result with extracted text in this case");
        }
        long total = sources.stream().mapToLong(source -> source.rawText().length() + 64L).sum();
        if (total > properties.getMaxInputChars()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AI input too large", "AI_INPUT_TOO_LARGE",
                    "Combined summary source text exceeds the configured AI input limit");
        }
    }

    private CaseSummaryResult requireResult(long caseId, long summaryId) {
        CaseSummaryResult result = mapper.selectDetail(caseId, summaryId);
        if (result == null) {
            throw notFound("Case summary not found", "CASE_SUMMARY_NOT_FOUND");
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
