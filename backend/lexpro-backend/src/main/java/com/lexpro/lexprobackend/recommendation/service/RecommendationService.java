package com.lexpro.lexprobackend.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.recommendation.client.RetrievalClient;
import com.lexpro.lexprobackend.recommendation.client.RetrievalClientException;
import com.lexpro.lexprobackend.recommendation.client.RetrievalContract;
import com.lexpro.lexprobackend.recommendation.config.RetrievalProperties;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationItemRecord;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationRecord;
import com.lexpro.lexprobackend.recommendation.domain.TypicalCaseRecord;
import com.lexpro.lexprobackend.recommendation.mapper.RecommendationMapper;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.ImportTypicalCasesRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationDetailResponse;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationSummaryResponse;
import com.lexpro.lexprobackend.recommendation.web.dto.RetrievalFiltersRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.TypicalCaseImportItemRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.TypicalCaseResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RecommendationService {

    private final RecommendationMapper mapper;
    private final RecommendationPersistenceService persistenceService;
    private final RetrievalClient retrievalClient;
    private final RetrievalProperties properties;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RecommendationService(RecommendationMapper mapper,
                                 RecommendationPersistenceService persistenceService,
                                 RetrievalClient retrievalClient,
                                 RetrievalProperties properties,
                                 CaseAccessService caseAccessService,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.persistenceService = persistenceService;
        this.retrievalClient = retrievalClient;
        this.properties = properties;
        this.caseAccessService = caseAccessService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<TypicalCaseResponse> importTypicalCases(long userId, ImportTypicalCasesRequest request,
                                                         String httpRequestId) {
        requireEnabled();
        Set<String> externalIds = new HashSet<>();
        if (request.cases().stream().anyMatch(item -> !externalIds.add(item.externalCaseId()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate typical cases", "TYPICAL_CASE_IMPORT_DUPLICATE",
                    "externalCaseId must be unique within one import request");
        }
        String requestId = UUID.randomUUID().toString();
        List<RetrievalContract.TypicalCaseInput> inputs = new ArrayList<>();
        for (TypicalCaseImportItemRequest item : request.cases()) {
            inputs.add(toContract(item));
        }
        try {
            RetrievalContract.NormalizeResponse response = retrievalClient.normalize(
                    new RetrievalContract.NormalizeRequest(RetrievalContract.SCHEMA_VERSION, requestId, inputs));
            validateNormalization(response, requestId, externalIds);
            List<Long> ids = persistenceService.importTypicalCases(userId, response, httpRequestId);
            return ids.stream().map(id -> toResponse(requireTypicalCase(id, userId))).toList();
        } catch (RetrievalClientException exception) {
            auditService.recordIndependent(new AuditEvent(userId, null, "TYPICAL_CASE_IMPORT_FAILED", "TYPICAL_CASE",
                    null, AuditResult.FAILED, Map.of("errorCode", exception.getErrorCode())), requestId);
            throw unavailable(exception.getErrorCode());
        } catch (IllegalStateException exception) {
            auditService.recordIndependent(new AuditEvent(userId, null, "TYPICAL_CASE_IMPORT_FAILED", "TYPICAL_CASE",
                    null, AuditResult.FAILED, Map.of("errorCode", "RETRIEVAL_RESPONSE_INVALID")), requestId);
            throw invalidResponse();
        } catch (RuntimeException exception) {
            auditService.recordIndependent(new AuditEvent(userId, null, "TYPICAL_CASE_IMPORT_FAILED", "TYPICAL_CASE",
                    null, AuditResult.FAILED, Map.of("errorCode", "TYPICAL_CASE_IMPORT_ERROR")), requestId);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<TypicalCaseResponse> typicalCases(long userId, String keyword, String caseCause,
                                                           String caseType, boolean favoritesOnly,
                                                           int page, int size) {
        int checkedPage = Math.max(page, 1);
        int checkedSize = Math.min(Math.max(size, 1), 100);
        long total = mapper.countTypicalCases(userId, trim(keyword), trim(caseCause), trim(caseType), favoritesOnly);
        List<TypicalCaseResponse> items = mapper.selectTypicalCases(
                userId, trim(keyword), trim(caseCause), trim(caseType), favoritesOnly,
                checkedSize, (long) (checkedPage - 1) * checkedSize
        ).stream().map(this::toResponse).toList();
        long totalPages = total == 0 ? 0 : (total + checkedSize - 1) / checkedSize;
        return new PageResponse<>(items, checkedPage, checkedSize, total, totalPages);
    }

    @Transactional(readOnly = true)
    public TypicalCaseResponse typicalCase(long userId, long typicalCaseId) {
        return toResponse(requireTypicalCase(typicalCaseId, userId));
    }

    @Transactional
    public void favorite(long userId, long typicalCaseId) {
        requireTypicalCase(typicalCaseId, userId);
        mapper.addFavorite(userId, typicalCaseId);
        auditService.record(new AuditEvent(userId, null, "TYPICAL_CASE_FAVORITED", "TYPICAL_CASE",
                String.valueOf(typicalCaseId), AuditResult.SUCCESS, Map.of()));
    }

    @Transactional
    public void unfavorite(long userId, long typicalCaseId) {
        requireTypicalCase(typicalCaseId, userId);
        mapper.removeFavorite(userId, typicalCaseId);
        auditService.record(new AuditEvent(userId, null, "TYPICAL_CASE_UNFAVORITED", "TYPICAL_CASE",
                String.valueOf(typicalCaseId), AuditResult.SUCCESS, Map.of()));
    }

    public RecommendationDetailResponse recommend(long userId, long caseId, CreateRecommendationRequest request,
                                                    String httpRequestId) {
        caseAccessService.requireRead(caseId, userId);
        requireEnabled();
        String factText = queryText(caseId, request);
        List<String> disputeFocus = normalizedList(request.disputeFocus());
        RetrievalContract.Filters filters = toFilters(request.filters());
        int limit = request.limit() == null ? 10 : request.limit();
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        try {
            RetrievalContract.RetrieveResponse response = retrievalClient.retrieve(new RetrievalContract.RetrieveRequest(
                    RetrievalContract.SCHEMA_VERSION, requestId, factText, disputeFocus, filters, limit));
            validateRetrieval(response, requestId, limit);
            int durationMs = elapsedMillis(started);
            long recommendId = persistenceService.saveRecommendation(
                    userId, caseId, request.sourceSummaryId(), factText, disputeFocus, filters,
                    response, durationMs, httpRequestId);
            return loadDetail(userId, caseId, recommendId);
        } catch (RetrievalClientException exception) {
            auditService.recordIndependent(new AuditEvent(userId, caseId, "CASE_RECOMMENDATION_FAILED",
                    "CASE_RECOMMENDATION", null, AuditResult.FAILED,
                    Map.of("errorCode", exception.getErrorCode())), requestId);
            throw unavailable(exception.getErrorCode());
        } catch (IllegalStateException exception) {
            auditService.recordIndependent(new AuditEvent(userId, caseId, "CASE_RECOMMENDATION_FAILED",
                    "CASE_RECOMMENDATION", null, AuditResult.FAILED,
                    Map.of("errorCode", "RETRIEVAL_RESPONSE_INVALID")), requestId);
            throw invalidResponse();
        } catch (RuntimeException exception) {
            auditService.recordIndependent(new AuditEvent(userId, caseId, "CASE_RECOMMENDATION_FAILED",
                    "CASE_RECOMMENDATION", null, AuditResult.FAILED,
                    Map.of("errorCode", "CASE_RECOMMENDATION_ERROR")), requestId);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<RecommendationSummaryResponse> history(long userId, long caseId) {
        caseAccessService.requireRead(caseId, userId);
        return mapper.selectRecommendationHistory(caseId).stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public RecommendationDetailResponse detail(long userId, long caseId, long recommendId) {
        caseAccessService.requireRead(caseId, userId);
        return loadDetail(userId, caseId, recommendId);
    }

    private RecommendationDetailResponse loadDetail(long userId, long caseId, long recommendId) {
        RecommendationRecord record = mapper.selectRecommendation(caseId, recommendId);
        if (record == null) {
            throw notFound("Recommendation not found", "CASE_RECOMMENDATION_NOT_FOUND");
        }
        List<RecommendationDetailResponse.Item> items = mapper.selectRecommendationItems(caseId, recommendId, userId)
                .stream().map(this::toItem).toList();
        return new RecommendationDetailResponse(
                record.recommendId(), record.caseId(), record.sourceSummaryId(), record.queryFactText(),
                json(record.queryDisputeFocusJson()), record.modelName(), record.modelVersion(),
                record.promptVersion(), json(record.queryParametersJson()), record.requestId(),
                record.durationMs(), record.createdAt(), items
        );
    }

    private void validateNormalization(RetrievalContract.NormalizeResponse response, String requestId,
                                       Set<String> externalIds) {
        validateModel(response == null ? null : response.model());
        if (response == null || !RetrievalContract.SCHEMA_VERSION.equals(response.schemaVersion())
                || !requestId.equals(response.requestId()) || response.cases() == null
                || response.cases().size() != externalIds.size()) {
            throw new IllegalStateException("Invalid retrieval normalization response");
        }
        Set<String> returned = new HashSet<>();
        for (RetrievalContract.NormalizedTypicalCase item : response.cases()) {
            if (item == null || !externalIds.contains(item.externalCaseId()) || !returned.add(item.externalCaseId())
                    || item.title() == null || item.title().isBlank()) {
                throw new IllegalStateException("Invalid normalized typical case");
            }
            validateVector(item.embedding());
        }
    }

    private void validateRetrieval(RetrievalContract.RetrieveResponse response, String requestId, int limit) {
        validateModel(response == null ? null : response.model());
        if (response == null || !RetrievalContract.SCHEMA_VERSION.equals(response.schemaVersion())
                || !requestId.equals(response.requestId()) || response.pipelineVersion() == null
                || response.pipelineVersion().isBlank() || response.index() == null || response.items() == null
                || response.items().size() > limit || (!response.degraded() && response.queryEmbedding() == null)) {
            throw new IllegalStateException("Invalid retrieval response");
        }
        if (response.queryEmbedding() != null) {
            validateVector(response.queryEmbedding());
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (int index = 0; index < response.items().size(); index++) {
            RetrievalContract.RetrievalItem item = response.items().get(index);
            if (item == null || item.typicalCaseId() <= 0 || item.rank() != index + 1
                    || !Double.isFinite(item.score()) || item.score() < 0 || item.score() > 1
                    || !ids.add(item.typicalCaseId()) || item.reasons() == null) {
                throw new IllegalStateException("Invalid ranked retrieval item");
            }
        }
    }

    private void validateModel(RetrievalContract.ModelInfo model) {
        if (model == null || !properties.getModelName().equals(model.name())
                || !properties.getModelVersion().equals(model.version())
                || properties.getDimension() != model.dimension()
                || !properties.getDistance().equals(model.distance())) {
            throw new IllegalStateException("Retrieval model contract mismatch");
        }
    }

    private void validateVector(List<Double> vector) {
        if (vector == null || vector.size() != properties.getDimension()
                || vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalStateException("Invalid retrieval vector");
        }
    }

    private String queryText(long caseId, CreateRecommendationRequest request) {
        if (request.factText() != null && !request.factText().isBlank()) {
            return request.factText().trim();
        }
        String summary = mapper.selectSummaryText(caseId, request.sourceSummaryId());
        if (summary == null || summary.isBlank()) {
            throw notFound("Case summary not found", "CASE_SUMMARY_NOT_FOUND");
        }
        return summary;
    }

    private RetrievalContract.TypicalCaseInput toContract(TypicalCaseImportItemRequest item) {
        return new RetrievalContract.TypicalCaseInput(
                item.externalCaseId(), item.externalCaseId(), item.title(), item.caseCause(), safe(item.caseCauses()),
                item.caseType(), item.country(), item.court(), item.courtLevel(), item.docType(),
                safe(item.disputeFocus()), item.judgmentDate(), item.procedure(), safe(item.applicableLaws()),
                item.caseLevel(), item.content(), item.fact()
        );
    }

    private RetrievalContract.Filters toFilters(RetrievalFiltersRequest filters) {
        if (filters == null) {
            return new RetrievalContract.Filters(null, null, null, null, null);
        }
        return new RetrievalContract.Filters(trim(filters.caseCause()), trim(filters.caseType()),
                trim(filters.courtLevel()), filters.judgmentYearFrom(), filters.judgmentYearTo());
    }

    private List<String> normalizedList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    private List<String> safe(List<String> values) { return values == null ? List.of() : List.copyOf(values); }

    private TypicalCaseRecord requireTypicalCase(long typicalCaseId, long userId) {
        TypicalCaseRecord record = mapper.selectTypicalCase(typicalCaseId, userId);
        if (record == null) {
            throw notFound("Typical case not found", "TYPICAL_CASE_NOT_FOUND");
        }
        return record;
    }

    private TypicalCaseResponse toResponse(TypicalCaseRecord record) {
        return new TypicalCaseResponse(
                record.typicalCaseId(), record.externalCaseId(), record.title(), record.caseCause(),
                json(record.caseCauseFullJson()), record.caseType(), record.country(), record.court(),
                record.courtLevel(), record.docType(), json(record.disputeFocusJson()), record.judgmentDate(),
                record.procedure(), json(record.applicableLawJson()), record.caseLevel(),
                record.embeddingModelName(), record.embeddingModelVersion(), record.embeddedAt(),
                record.createdAt(), record.updatedAt(), record.content(), record.fact(), record.favorite()
        );
    }

    private RecommendationSummaryResponse toSummary(RecommendationRecord record) {
        return new RecommendationSummaryResponse(record.recommendId(), record.sourceSummaryId(), record.modelName(),
                record.modelVersion(), record.promptVersion(), record.requestId(), record.durationMs(), record.createdAt());
    }

    private RecommendationDetailResponse.Item toItem(RecommendationItemRecord item) {
        return new RecommendationDetailResponse.Item(
                item.itemId(), item.typicalCaseId(), item.rankNo(), item.similarityScore(), json(item.reasonJson()),
                item.externalCaseId(), item.title(), item.caseCause(), item.court(), item.courtLevel(),
                item.judgmentDate(), item.favorite()
        );
    }

    private JsonNode json(String value) {
        try {
            return value == null ? null : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored retrieval JSON is invalid", exception);
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw unavailable("RETRIEVAL_DISABLED");
        }
    }

    private int elapsedMillis(long started) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000L);
    }

    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private ApiException unavailable(String errorCode) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Typical-case retrieval unavailable", errorCode,
                "The typical-case retrieval service is not available");
    }

    private ApiException invalidResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Invalid retrieval response", "RETRIEVAL_RESPONSE_INVALID",
                "The retrieval service returned a response that failed contract validation");
    }

    private ApiException notFound(String title, String errorCode) {
        return new ApiException(HttpStatus.NOT_FOUND, title, errorCode, "The requested resource does not exist");
    }
}
