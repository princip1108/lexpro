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
import com.lexpro.lexprobackend.recommendation.config.PartnerTypicalCaseProperties;
import com.lexpro.lexprobackend.recommendation.config.TypicalCaseProvider;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationItemRecord;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationRecord;
import com.lexpro.lexprobackend.recommendation.domain.TypicalCaseRecord;
import com.lexpro.lexprobackend.recommendation.mapper.RecommendationMapper;
import com.lexpro.lexprobackend.recommendation.partner.AnalysisTokenException;
import com.lexpro.lexprobackend.recommendation.partner.AnalysisTokenService;
import com.lexpro.lexprobackend.recommendation.partner.PartnerTypicalCaseClient;
import com.lexpro.lexprobackend.recommendation.partner.PartnerTypicalCaseClientException;
import com.lexpro.lexprobackend.recommendation.partner.PartnerTypicalCaseContract;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationAnalysisRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.ImportTypicalCasesRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationDetailResponse;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationAnalysisResponse;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationSummaryResponse;
import com.lexpro.lexprobackend.recommendation.web.dto.RetrievalFiltersRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.PartnerRecommendationFiltersRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.TypicalCaseImportItemRequest;

import java.time.LocalDate;
import com.lexpro.lexprobackend.recommendation.web.dto.TypicalCaseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationService.class);

    public record McpRecommendationResult(
            PartnerTypicalCaseContract.AnalyzeData analysis,
            PartnerTypicalCaseContract.SearchData search
    ) {}

    private final RecommendationMapper mapper;
    private final RecommendationPersistenceService persistenceService;
    private final RetrievalClient retrievalClient;
    private final RetrievalProperties properties;
    private final PartnerTypicalCaseProperties partnerProperties;
    private final PartnerTypicalCaseClient partnerClient;
    private final AnalysisTokenService analysisTokenService;
    private final CaseAccessService caseAccessService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public RecommendationService(RecommendationMapper mapper,
                                 RecommendationPersistenceService persistenceService,
                                 RetrievalClient retrievalClient,
                                 RetrievalProperties properties,
                                 PartnerTypicalCaseProperties partnerProperties,
                                 PartnerTypicalCaseClient partnerClient,
                                 AnalysisTokenService analysisTokenService,
                                 CaseAccessService caseAccessService,
                                 AuditService auditService,
                                 ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.persistenceService = persistenceService;
        this.retrievalClient = retrievalClient;
        this.properties = properties;
        this.partnerProperties = partnerProperties;
        this.partnerClient = partnerClient;
        this.analysisTokenService = analysisTokenService;
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
                                                           String caseType, String court, String region,
                                                           String docType, String sourceName, String caseLevel,
                                                           LocalDate judgmentDate, LocalDate judgmentDateFrom, LocalDate judgmentDateTo,
                                                           boolean favoritesOnly,
                                                           int page, int size) {
        int checkedPage = Math.max(page, 1);
        if (judgmentDateFrom != null && judgmentDateTo != null && judgmentDateFrom.isAfter(judgmentDateTo)) {
            throw new com.lexpro.lexprobackend.common.error.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid date range", "INVALID_DATE_RANGE", "起始日期不能晚于结束日期");
        }
        int checkedSize = Math.min(Math.max(size, 1), 100);
        long total = mapper.countTypicalCases(userId, trim(keyword), trim(caseCause), trim(caseType),
                trim(court), trim(region), trim(docType), trim(sourceName), trim(caseLevel),
                judgmentDate, judgmentDateFrom, judgmentDateTo, favoritesOnly);
        List<TypicalCaseResponse> items = mapper.selectTypicalCases(
                userId, trim(keyword), trim(caseCause), trim(caseType), trim(court), trim(region),
                trim(docType), trim(sourceName), trim(caseLevel), judgmentDate, judgmentDateFrom, judgmentDateTo, favoritesOnly,
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
        if (properties.getProvider() == TypicalCaseProvider.PARTNER) {
            return recommendWithPartner(userId, caseId, request, factText, httpRequestId);
        }
        return recommendLocally(userId, caseId, request, factText, httpRequestId);
    }

    /**
     * Runs the partner recommendation pipeline for the stateless MCP boundary.
     * MCP callers provide an already-authorized fact snapshot, so this path does
     * not create case history or impersonate a web application user.
     */
    public McpRecommendationResult recommendForMcp(
            String factText, PartnerRecommendationFiltersRequest filters, Integer requestedLimit) {
        requirePartnerEnabled();
        requirePartnerCaseDataAllowed();
        int limit = requestedLimit == null ? Math.min(10, partnerProperties.getMaxResults()) : requestedLimit;
        if (limit < 1 || limit > partnerProperties.getMaxResults()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Too many recommendation results",
                    "TYPICAL_CASE_RESULT_LIMIT_EXCEEDED",
                    "The requested result limit exceeds the configured partner limit");
        }
        String normalizedFactText = factText == null ? null : factText.trim();
        if (normalizedFactText == null || normalizedFactText.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fact text is required", "MCP_INPUT_INVALID",
                    "factText must be a nonblank string");
        }
        try {
            PartnerTypicalCaseContract.AnalyzeData analysis = validatePartnerAnalysis(
                    partnerClient.analyze(new PartnerTypicalCaseContract.AnalyzeRequest(normalizedFactText)));
            PartnerTypicalCaseContract.Filters partnerFilters = toPartnerFilters(filters);
            PartnerTypicalCaseContract.SearchData search = validatePartnerSearch(
                    partnerClient.search(new PartnerTypicalCaseContract.SearchRequest(
                            analysis.analysisId(), partnerFilters, limit)), analysis.analysisId(), limit);
            return new McpRecommendationResult(analysis, search);
        } catch (PartnerTypicalCaseClientException exception) {
            throw partnerError(exception.getKind());
        } catch (IllegalStateException exception) {
            LOGGER.warn("mcp_partner_recommendation_response_invalid reason={}", exception.getMessage());
            throw partnerInvalidResponse();
        }
    }

    public RecommendationAnalysisResponse analyze(long userId, long caseId,
                                                   CreateRecommendationAnalysisRequest request,
                                                   String httpRequestId) {
        caseAccessService.requireRead(caseId, userId);
        requirePartnerEnabled();
        requirePartnerCaseDataAllowed();
        String factText = queryText(caseId, request.sourceSummaryId(), request.factText());
        String requestId = UUID.randomUUID().toString();
        try {
            PartnerTypicalCaseContract.AnalyzeEnvelope envelope = partnerClient.analyze(
                    new PartnerTypicalCaseContract.AnalyzeRequest(factText));
            PartnerTypicalCaseContract.AnalyzeData data = validatePartnerAnalysis(envelope);
            List<AnalysisTokenService.TokenIssue> tokenIssues = data.issues().stream()
                    .map(issue -> new AnalysisTokenService.TokenIssue(
                            issue.issueId(), issue.issueText().trim(), issue.reliability(), issue.weight()))
                    .toList();
            AnalysisTokenService.IssuedToken token = analysisTokenService.issue(
                    data.analysisId(), userId, caseId, factText, tokenIssues);
            Map<String, Object> detail = new java.util.LinkedHashMap<>();
            detail.put("provider", TypicalCaseProvider.PARTNER.name());
            detail.put("issueCount", data.issues().size());
            if (httpRequestId != null && !httpRequestId.isBlank()) {
                detail.put("httpRequestId", httpRequestId);
            }
            auditService.recordIndependent(new AuditEvent(userId, caseId, "CASE_RECOMMENDATION_ANALYZED",
                    "CASE_RECOMMENDATION", null, AuditResult.SUCCESS, detail), requestId);
            return new RecommendationAnalysisResponse(
                    token.value(), token.expiresAt(), data.sentenceCount(), data.issueCount(),
                    new RecommendationAnalysisResponse.Timings(
                            data.qwenTimeMs(), data.deltaTimeMs(), data.totalTimeMs()),
                    data.issues().stream().map(issue -> new RecommendationAnalysisResponse.Issue(
                            issue.issueId(), issue.issueText(), issue.reliability(), issue.weight(),
                            issue.matchedSentenceIndex(), issue.matchedSentence())).toList()
            );
        } catch (PartnerTypicalCaseClientException exception) {
            auditPartnerFailure(userId, caseId, "CASE_RECOMMENDATION_ANALYSIS_FAILED", exception.getKind(), requestId);
            throw partnerError(exception.getKind());
        } catch (IllegalStateException exception) {
            LOGGER.warn("partner_analysis_response_invalid requestId={} reason={}", requestId,
                    exception.getMessage());
            auditPartnerFailure(userId, caseId, "CASE_RECOMMENDATION_ANALYSIS_FAILED",
                    PartnerTypicalCaseClientException.Kind.RESPONSE_INVALID, requestId);
            throw partnerInvalidResponse();
        }
    }

    private RecommendationDetailResponse recommendLocally(long userId, long caseId,
                                                            CreateRecommendationRequest request,
                                                            String factText, String httpRequestId) {
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

    private RecommendationDetailResponse recommendWithPartner(long userId, long caseId,
                                                                CreateRecommendationRequest request,
                                                                String factText, String httpRequestId) {
        requirePartnerCaseDataAllowed();
        String requestId = UUID.randomUUID().toString();
        try {
            AnalysisTokenService.TokenPayload token = analysisTokenService.verify(
                    request.analysisToken(), userId, caseId, factText);
            PartnerTypicalCaseContract.Filters filters = toPartnerFilters(request.partnerFilters());
            int limit = request.limit() == null ? Math.min(10, partnerProperties.getMaxResults()) : request.limit();
            if (limit > partnerProperties.getMaxResults()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Too many recommendation results",
                        "TYPICAL_CASE_RESULT_LIMIT_EXCEEDED",
                        "The requested result limit exceeds the configured partner limit");
            }
            long started = System.nanoTime();
            PartnerTypicalCaseContract.SearchEnvelope envelope = partnerClient.search(
                    new PartnerTypicalCaseContract.SearchRequest(token.providerAnalysisId(), filters, limit));
            PartnerTypicalCaseContract.SearchData response = validatePartnerSearch(
                    envelope, token.providerAnalysisId(), limit);
            int durationMs = elapsedMillis(started);
            long recommendId = persistenceService.savePartnerRecommendation(
                    userId, caseId, request.sourceSummaryId(), factText, filters,
                    token.issues(), response, durationMs, requestId, httpRequestId);
            return loadDetail(userId, caseId, recommendId);
        } catch (AnalysisTokenException exception) {
            auditTokenFailure(userId, caseId, exception.getKind(), requestId);
            throw tokenError(exception.getKind());
        } catch (PartnerTypicalCaseClientException exception) {
            auditPartnerFailure(userId, caseId, "CASE_RECOMMENDATION_FAILED", exception.getKind(), requestId);
            throw partnerError(exception.getKind());
        } catch (IllegalStateException exception) {
            LOGGER.warn("partner_search_response_invalid requestId={} reason={}", requestId,
                    exception.getMessage());
            auditPartnerFailure(userId, caseId, "CASE_RECOMMENDATION_FAILED",
                    PartnerTypicalCaseClientException.Kind.RESPONSE_INVALID, requestId);
            throw partnerInvalidResponse();
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
                record.promptVersion(), publicParameters(record.queryParametersJson()), record.requestId(),
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

    private PartnerTypicalCaseContract.AnalyzeData validatePartnerAnalysis(
            PartnerTypicalCaseContract.AnalyzeEnvelope envelope) {
        PartnerTypicalCaseContract.AnalyzeData data = envelope == null ? null : envelope.data();
        if (envelope == null || envelope.code() != 0 || data == null
                || data.analysisId() == null || data.analysisId().isBlank() || data.analysisId().length() > 200
                || data.sentenceCount() == null || data.sentenceCount() < 1
                || data.issueCount() == null || data.issueCount() < 1 || data.issueCount() > 100
                || data.issues() == null || data.issues().size() != data.issueCount()
                || !finiteNonNegative(data.qwenTimeMs()) || !finiteNonNegative(data.deltaTimeMs())
                || !finiteNonNegative(data.totalTimeMs())) {
            throw new IllegalStateException("Invalid partner analysis response");
        }
        Set<Integer> issueIds = new HashSet<>();
        for (PartnerTypicalCaseContract.AnalyzeIssue issue : data.issues()) {
            if (issue == null || issue.issueId() == null || issue.issueId() < 0 || !issueIds.add(issue.issueId())
                    || issue.issueText() == null || issue.issueText().isBlank() || issue.issueText().length() > 1_000
                    || !finiteUnit(issue.reliability()) || !finiteUnit(issue.weight())
                    || issue.matchedSentenceIndex() == null || issue.matchedSentenceIndex() < 0
                    || issue.matchedSentence() == null || issue.matchedSentence().isBlank()
                    || issue.matchedSentence().length() > 10_000) {
                throw new IllegalStateException("Invalid partner analysis issue");
            }
        }
        return data;
    }

    private PartnerTypicalCaseContract.SearchData validatePartnerSearch(
            PartnerTypicalCaseContract.SearchEnvelope envelope, String analysisId, int limit) {
        PartnerTypicalCaseContract.SearchData data = envelope == null ? null : normalizePartnerSearch(envelope.data());
        if (envelope == null || envelope.code() != 0 || data == null
                || !analysisId.equals(data.analysisId()) || data.retrievalId() == null
                || data.retrievalId().isBlank() || data.retrievalId().length() > 200
                || data.candidateCount() == null || data.candidateCount() < 0
                || data.candidates() == null || data.candidateCount() != data.candidates().size()
                || data.candidates().size() > limit || data.rankingRule() == null || data.rankingRule().isBlank()
                || data.rankingRule().length() > 500 || data.scoreWeights() == null
                || data.timing() == null || data.pipelineTiming() == null) {
            throw new IllegalStateException("Invalid partner search response");
        }
        Set<String> externalIds = new HashSet<>();
        for (int index = 0; index < data.candidates().size(); index++) {
            PartnerTypicalCaseContract.Candidate item = data.candidates().get(index);
            String candidateError = partnerCandidateError(item, index, externalIds);
            if (candidateError != null) {
                throw new IllegalStateException("Invalid partner recommendation candidate: " + candidateError);
            }
        }
        return data;
    }

    private PartnerTypicalCaseContract.SearchData normalizePartnerSearch(
            PartnerTypicalCaseContract.SearchData data) {
        if (data == null || data.candidates() == null) {
            return data;
        }
        List<PartnerTypicalCaseContract.Candidate> candidates = data.candidates().stream()
                .map(this::normalizePartnerCandidate)
                .toList();
        return new PartnerTypicalCaseContract.SearchData(
                data.retrievalId(), data.analysisId(), data.candidateCount(), data.rankingRule(), data.scoreWeights(),
                candidates, data.timing(), data.pipelineTiming());
    }

    private PartnerTypicalCaseContract.Candidate normalizePartnerCandidate(
            PartnerTypicalCaseContract.Candidate item) {
        if (item == null) {
            return null;
        }
        return new PartnerTypicalCaseContract.Candidate(
                item.id(), partnerCandidateTitle(item), item.caseid(), cleanPartnerTextList(item.applicableLaw()), item.casecause(),
                cleanPartnerTextList(item.casecausefull()), item.caselevel(), item.casetype(), item.county(),
                item.court(), item.courtlevel(), item.judgedate(), item.procedure(), item.doctype(), item.distance(),
                item.factSimilarity(), item.coreTable(), item.contentTable(), item.rank(), item.retrievalRank(),
                item.issueRawScore(), item.issueScore(), item.finalScore(), item.issueDetails(), item.content());
    }

    private String partnerCandidateTitle(PartnerTypicalCaseContract.Candidate item) {
        String title = trim(item.title());
        if (title == null) {
            title = trim(item.caseid());
        }
        if (title == null) {
            title = "Typical case " + item.id();
        }
        return title.length() <= 255 ? title : title.substring(0, 255);
    }

    private List<String> cleanPartnerTextList(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private String partnerCandidateError(PartnerTypicalCaseContract.Candidate item, int index,
                                         Set<String> externalIds) {
        if (item == null) return "index=" + index + ", candidate is null";
        if (item.id() == null || item.id() <= 0) return "index=" + index + ", id";
        if (item.rank() == null || item.rank() != index + 1) return "index=" + index + ", rank";
        if (item.title() == null || item.title().isBlank() || item.title().length() > 255) {
            return "index=" + index + ", title";
        }
        if (item.coreTable() == null || !Set.of("core_typical", "core_2025", "core_2024", "core_2023")
                .contains(item.coreTable())) return "index=" + index + ", coreTable";
        if (!matchesContentTable(item.coreTable(), item.contentTable())) return "index=" + index + ", contentTable";
        if (!externalIds.add(partnerExternalCaseId(item))) return "index=" + index + ", duplicate external id";
        if (!validOptional(item.caseid(), 100)) return "index=" + index + ", caseid";
        if (!validOptional(item.casecause(), 255)) return "index=" + index + ", casecause";
        if (!validOptionalStringList(item.casecausefull(), 100, 255)) return "index=" + index + ", casecausefull";
        if (!validOptional(item.caselevel(), 50)) return "index=" + index + ", caselevel";
        if (!validOptional(item.casetype(), 50)) return "index=" + index + ", casetype";
        if (!validOptional(item.county(), 100)) return "index=" + index + ", county";
        if (!validOptional(item.court(), 255)) return "index=" + index + ", court";
        if (!validOptional(item.courtlevel(), 50)) return "index=" + index + ", courtlevel";
        if (!validOptional(item.procedure(), 100)) return "index=" + index + ", procedure";
        if (!validOptional(item.doctype(), 50)) return "index=" + index + ", doctype";
        if (!validOptionalStringList(item.applicableLaw(), 100, 500)) return "index=" + index + ", applicableLaw";
        if (!finiteRange(item.factSimilarity(), -1, 1)) return "index=" + index + ", factSimilarity";
        if (!finiteRange(item.finalScore(), -1, 2)) return "index=" + index + ", finalScore";
        if (!finiteRange(item.issueScore(), 0, 1)) return "index=" + index + ", issueScore";
        if (item.issueRawScore() == null || !Double.isFinite(item.issueRawScore())) return "index=" + index + ", issueRawScore";
        if (item.distance() == null || !Double.isFinite(item.distance())) return "index=" + index + ", distance";
        if (item.retrievalRank() == null || item.retrievalRank() < 1) return "index=" + index + ", retrievalRank";
        if (item.issueDetails() == null || item.issueDetails().size() > 100) return "index=" + index + ", issueDetails";
        if (item.content() != null && item.content().length() > 5_000_000) return "index=" + index + ", content";
        return null;
    }

    private boolean matchesContentTable(String coreTable, String contentTable) {
        return switch (coreTable) {
            case "core_typical" -> "content_typical".equals(contentTable);
            case "core_2025" -> "content_2025".equals(contentTable);
            case "core_2024" -> "content_2024".equals(contentTable);
            case "core_2023" -> "content_2023".equals(contentTable);
            default -> false;
        };
    }

    private boolean validOptional(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength;
    }

    private boolean validOptionalStringList(List<String> values, int maximumItems, int maximumItemLength) {
        return values == null || values.size() <= maximumItems
                && values.stream().allMatch(value -> value != null && !value.isBlank()
                && value.length() <= maximumItemLength);
    }

    static String partnerExternalCaseId(PartnerTypicalCaseContract.Candidate item) {
        return "partner:" + item.coreTable() + ":" + item.id();
    }

    private PartnerTypicalCaseContract.Filters toPartnerFilters(PartnerRecommendationFiltersRequest filters) {
        if (filters == null) {
            return new PartnerTypicalCaseContract.Filters(
                    null, null, null, null, null, null, null, null, null, null, null);
        }
        return new PartnerTypicalCaseContract.Filters(
                trim(filters.title()), normalizedNullableList(filters.caseCauses()),
                normalizedNullableList(filters.applicableLaws()), trim(filters.caseLevel()),
                trim(filters.courtLevel()), trim(filters.region()), filters.judgmentDateThrough(),
                trim(filters.procedure()), trim(filters.docType()), trim(filters.court()), trim(filters.caseType()));
    }

    private List<String> normalizedNullableList(List<String> values) {
        List<String> normalized = normalizedList(values);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean finiteNonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0;
    }

    private boolean finiteUnit(Double value) {
        return finiteRange(value, 0, 1);
    }

    private boolean finiteRange(Double value, double minimum, double maximum) {
        return value != null && Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private String queryText(long caseId, CreateRecommendationRequest request) {
        return queryText(caseId, request.sourceSummaryId(), request.factText());
    }

    private String queryText(long caseId, Long sourceSummaryId, String suppliedFactText) {
        if (suppliedFactText != null && !suppliedFactText.isBlank()) {
            return suppliedFactText.trim();
        }
        String summary = mapper.selectSummaryText(caseId, sourceSummaryId);
        if (summary == null || summary.isBlank()) {
            throw notFound("Case summary not found", "CASE_SUMMARY_NOT_FOUND");
        }
        return summary.trim();
    }

    private RetrievalContract.TypicalCaseInput toContract(TypicalCaseImportItemRequest item) {
        return new RetrievalContract.TypicalCaseInput(
                item.externalCaseId(), item.externalCaseId(), item.title(), item.caseCause(), safe(item.caseCauses()),
                item.caseType(), item.country(), item.court(), item.courtLevel(), item.docType(),
                safe(item.disputeFocus()), item.judgmentDate(), item.procedure(), safe(item.applicableLaws()),
                item.caseLevel(), item.sourceName(), item.sourceFile(), item.sourceUrl(), safe(item.keywords()),
                item.content(), item.fact(), item.summary(), item.prosecutorialProcess(),
                item.adjudicationResult(), item.reasoning(), item.guidingSignificance()
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
                record.typicalCaseId(), externalCaseIdForResponse(record.externalCaseId()), record.caseNumber(),
                record.title(), record.caseCause(),
                json(record.caseCauseFullJson()), record.caseType(), record.country(), record.region(), record.court(),
                record.courtLevel(), record.docType(), json(record.disputeFocusJson()), record.judgmentDate(),
                record.procedure(), json(record.applicableLawJson()), record.caseLevel(),
                record.sourceName(), record.sourceFile(), record.sourceUrl(), json(record.keywordsJson()),
                record.embeddingModelName(), record.embeddingModelVersion(), record.embeddedAt(),
                record.createdAt(), record.updatedAt(), record.content(), record.fact(), record.summary(),
                record.prosecutorialProcess(), record.adjudicationResult(), record.reasoning(),
                record.guidingSignificance(), record.favorite()
        );
    }

    private RecommendationSummaryResponse toSummary(RecommendationRecord record) {
        return new RecommendationSummaryResponse(record.recommendId(), record.sourceSummaryId(), record.modelName(),
                record.modelVersion(), record.promptVersion(), record.requestId(), record.durationMs(), record.createdAt());
    }

    private RecommendationDetailResponse.Item toItem(RecommendationItemRecord item) {
        return new RecommendationDetailResponse.Item(
                item.itemId(), item.typicalCaseId(), item.rankNo(), item.similarityScore(), item.rankingScore(),
                json(item.reasonJson()), externalCaseIdForResponse(item.externalCaseId()), item.caseNumber(),
                item.title(), item.caseCause(),
                json(item.caseCauseFullJson()), item.caseType(), item.region(), item.court(), item.courtLevel(),
                item.docType(), json(item.applicableLawJson()), item.caseLevel(), item.judgmentDate(), item.favorite()
        );
    }

    private String externalCaseIdForResponse(String externalCaseId) {
        return externalCaseId != null && externalCaseId.startsWith("partner:") ? null : externalCaseId;
    }

    private JsonNode publicParameters(String parametersJson) {
        JsonNode parameters = json(parametersJson);
        if (parameters != null && parameters.isObject() && "PARTNER".equals(parameters.path("provider").textValue())) {
            com.fasterxml.jackson.databind.node.ObjectNode sanitized = parameters.deepCopy();
            sanitized.remove(List.of("providerAnalysisId", "providerRetrievalId"));
            return sanitized;
        }
        return parameters;
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

    private void requirePartnerEnabled() {
        requireEnabled();
        if (properties.getProvider() != TypicalCaseProvider.PARTNER) {
            throw new ApiException(HttpStatus.CONFLICT, "Partner provider is not active",
                    "TYPICAL_CASE_PARTNER_PROVIDER_INACTIVE",
                    "The partner typical-case provider is not active in this deployment");
        }
    }

    private void requirePartnerCaseDataAllowed() {
        if (!partnerProperties.isAllowCaseData()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "External case-data transfer disabled",
                    "TYPICAL_CASE_EXTERNAL_DATA_DISABLED",
                    "This deployment does not allow case data to be sent to the partner service");
        }
    }

    private void auditPartnerFailure(long userId, long caseId, String operation,
                                     PartnerTypicalCaseClientException.Kind kind, String requestId) {
        auditService.recordIndependent(new AuditEvent(userId, caseId, operation, "CASE_RECOMMENDATION",
                null, AuditResult.FAILED, Map.of("errorCode", kind.name())), requestId);
    }

    private void auditTokenFailure(long userId, long caseId, AnalysisTokenException.Kind kind, String requestId) {
        auditService.recordIndependent(new AuditEvent(userId, caseId, "CASE_RECOMMENDATION_FAILED",
                "CASE_RECOMMENDATION", null, AuditResult.FAILED,
                Map.of("errorCode", "ANALYSIS_TOKEN_" + kind.name())), requestId);
    }

    private ApiException partnerError(PartnerTypicalCaseClientException.Kind kind) {
        return switch (kind) {
            case ANALYSIS_EXPIRED -> new ApiException(HttpStatus.CONFLICT, "Recommendation analysis expired",
                    "TYPICAL_CASE_ANALYSIS_EXPIRED",
                    "The partner analysis no longer exists; run analysis again");
            case RESPONSE_INVALID -> partnerInvalidResponse();
            case UNAVAILABLE -> unavailable("TYPICAL_CASE_PROVIDER_UNAVAILABLE");
        };
    }

    private ApiException tokenError(AnalysisTokenException.Kind kind) {
        return switch (kind) {
            case EXPIRED -> new ApiException(HttpStatus.CONFLICT, "Recommendation analysis expired",
                    "TYPICAL_CASE_ANALYSIS_EXPIRED", "The recommendation analysis token has expired");
            case BINDING_MISMATCH -> new ApiException(HttpStatus.CONFLICT, "Recommendation analysis mismatch",
                    "TYPICAL_CASE_ANALYSIS_MISMATCH",
                    "The analysis token does not match the current user, case or fact text");
            case INVALID -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid recommendation analysis token",
                    "TYPICAL_CASE_ANALYSIS_TOKEN_INVALID", "The recommendation analysis token is invalid");
        };
    }

    private ApiException partnerInvalidResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Invalid partner response",
                "TYPICAL_CASE_PROVIDER_RESPONSE_INVALID",
                "The partner typical-case service returned an invalid response");
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
