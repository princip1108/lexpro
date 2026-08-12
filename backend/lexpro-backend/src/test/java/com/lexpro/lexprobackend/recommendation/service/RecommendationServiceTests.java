package com.lexpro.lexprobackend.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.recommendation.client.RetrievalClient;
import com.lexpro.lexprobackend.recommendation.client.RetrievalContract;
import com.lexpro.lexprobackend.recommendation.config.RetrievalProperties;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationItemRecord;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationRecord;
import com.lexpro.lexprobackend.recommendation.mapper.RecommendationMapper;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceTests {

    @Test
    void shouldAuthorizeRetrievePersistAndReturnRankedResult() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RecommendationPersistenceService persistence = mock(RecommendationPersistenceService.class);
        RetrievalClient client = mock(RetrievalClient.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        AuditService auditService = mock(AuditService.class);
        RetrievalProperties properties = properties();
        when(client.retrieve(any())).thenAnswer(invocation -> {
            RetrievalContract.RetrieveRequest request = invocation.getArgument(0);
            return new RetrievalContract.RetrieveResponse(
                    "1.0", request.requestId(),
                    new RetrievalContract.ModelInfo("BAAI/bge-m3", "main", 1024, "cosine"),
                    "m7-hybrid-v1", Map.of("type", "HNSW"), false, null,
                    Collections.nCopies(1024, 0.0),
                    List.of(new RetrievalContract.RetrievalItem(
                            8L, 1, 0.91, 0.7, 0.88,
                            List.of(Map.of("type", "VECTOR_SIMILARITY", "score", 0.88))))
            );
        });
        when(persistence.saveRecommendation(anyLong(), anyLong(), any(), any(), any(), any(), any(), anyInt(), any()))
                .thenReturn(30L);
        OffsetDateTime now = OffsetDateTime.now();
        when(mapper.selectRecommendation(9L, 30L)).thenReturn(new RecommendationRecord(
                30L, 9L, null, "合同货款争议", "[]", "BAAI/bge-m3", "main",
                "m7-hybrid-v1", "{}", "req-1", 12, 7L, now));
        when(mapper.selectRecommendationItems(9L, 30L, 7L)).thenReturn(List.of(new RecommendationItemRecord(
                40L, 8L, 1, new BigDecimal("0.910000"), "[]", "ext-8", "典型案例",
                "买卖合同纠纷", "某法院", "中级", LocalDate.of(2025, 1, 1), false)));
        RecommendationService service = new RecommendationService(
                mapper, persistence, client, properties, accessService, auditService, new ObjectMapper());

        var result = service.recommend(7L, 9L,
                new CreateRecommendationRequest(null, "合同货款争议", List.of("货款支付"), null, 10), "http-1");

        assertEquals(30L, result.recommendId());
        assertEquals(8L, result.items().getFirst().typicalCaseId());
        verify(accessService).requireRead(9L, 7L);
        verify(persistence).saveRecommendation(eq(7L), eq(9L), isNull(), eq("合同货款争议"),
                eq(List.of("货款支付")), any(), any(), anyInt(), eq("http-1"));
    }

    private RetrievalProperties properties() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setEnabled(true);
        properties.setModelName("BAAI/bge-m3");
        properties.setModelVersion("main");
        properties.setDimension(1024);
        properties.setDistance("cosine");
        return properties;
    }
}
