package com.lexpro.lexprobackend.recommendation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.recommendation.client.RetrievalContract;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationWrite;
import com.lexpro.lexprobackend.recommendation.mapper.RecommendationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecommendationPersistenceService {

    private final RecommendationMapper mapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public RecommendationPersistenceService(RecommendationMapper mapper, ObjectMapper objectMapper,
                                            AuditService auditService) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public List<Long> importTypicalCases(long userId, RetrievalContract.NormalizeResponse response,
                                         String httpRequestId) {
        List<Long> ids = new ArrayList<>(response.cases().size());
        for (RetrievalContract.NormalizedTypicalCase item : response.cases()) {
            long typicalCaseId = mapper.upsertTypicalCase(
                    item,
                    json(item.caseCauses()),
                    json(item.disputeFocus()),
                    json(item.applicableLaws()),
                    vector(item.embedding()),
                    response.model().name(),
                    response.model().version()
            );
            mapper.upsertTypicalCaseContent(typicalCaseId, item.content(), item.fact());
            ids.add(typicalCaseId);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("count", ids.size());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            detail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, null, "TYPICAL_CASES_IMPORTED", "TYPICAL_CASE",
                null, AuditResult.SUCCESS, detail), response.requestId());
        return List.copyOf(ids);
    }

    @Transactional
    public long saveRecommendation(long userId, long caseId, Long sourceSummaryId, String factText,
                                   List<String> disputeFocus, RetrievalContract.Filters filters,
                                   RetrievalContract.RetrieveResponse response, int durationMs,
                                   String httpRequestId) {
        List<Long> ids = response.items().stream().map(RetrievalContract.RetrievalItem::typicalCaseId).toList();
        if (!ids.isEmpty() && mapper.countTypicalCaseIds(ids) != ids.size()) {
            throw new IllegalStateException("Retrieval response references unknown typical cases");
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("schemaVersion", response.schemaVersion());
        parameters.put("filters", filters);
        parameters.put("index", response.index());
        parameters.put("degraded", response.degraded());
        if (response.degradationReason() != null) {
            parameters.put("degradationReason", response.degradationReason());
        }

        RecommendationWrite write = new RecommendationWrite();
        write.setCaseId(caseId);
        write.setSourceSummaryId(sourceSummaryId);
        write.setQueryFactText(factText);
        write.setQueryEmbedding(response.queryEmbedding() == null ? null : vector(response.queryEmbedding()));
        write.setQueryDisputeFocusJson(json(disputeFocus));
        write.setModelName(response.model().name());
        write.setModelVersion(response.model().version());
        write.setPipelineVersion(response.pipelineVersion());
        write.setQueryParametersJson(json(parameters));
        write.setRequestId(response.requestId());
        write.setDurationMs(durationMs);
        write.setCreatedBy(userId);
        mapper.insertRecommendation(write);

        for (RetrievalContract.RetrievalItem item : response.items()) {
            mapper.insertRecommendationItem(
                    write.getRecommendId(), caseId, item.typicalCaseId(), item.rank(),
                    BigDecimal.valueOf(item.score()), json(item.reasons())
            );
        }
        Map<String, Object> auditDetail = new LinkedHashMap<>();
        auditDetail.put("count", response.items().size());
        auditDetail.put("degraded", response.degraded());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            auditDetail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_RECOMMENDATION_CREATED",
                "CASE_RECOMMENDATION", String.valueOf(write.getRecommendId()),
                AuditResult.SUCCESS, auditDetail), response.requestId());
        return write.getRecommendId();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Retrieval data must be JSON serializable", exception);
        }
    }

    private String vector(List<Double> values) {
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
