package com.lexpro.lexprobackend.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationWrite;
import com.lexpro.lexprobackend.recommendation.mapper.RecommendationMapper;
import com.lexpro.lexprobackend.recommendation.partner.AnalysisTokenService;
import com.lexpro.lexprobackend.recommendation.partner.PartnerTypicalCaseContract;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationPersistenceServiceTests {

    @Test
    void shouldMirrorPartnerCaseWithoutVectorAndPersistSeparateScores() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        AuditService auditService = mock(AuditService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RecommendationPersistenceService service = new RecommendationPersistenceService(
                mapper, objectMapper, auditService);
        PartnerTypicalCaseContract.Candidate candidate = new PartnerTypicalCaseContract.Candidate(
                18L, "案例标题", "（2025）京01号", List.of("民法典第五百零九条"),
                "合同纠纷", List.of("合同纠纷"), "典型案例", "民事", "北京市", "某法院",
                "中级法院", LocalDate.of(2025, 1, 2), "二审", "判决书", 0.12, 0.88,
                "core_typical", "content_typical", 1, 2, 0.4, 0.7, 1.09,
                List.of(Map.of("issue_text", "合同效力", "weighted_score", 0.3)), "案例正文");
        PartnerTypicalCaseContract.Filters filters = new PartnerTypicalCaseContract.Filters(
                null, null, null, null, null, "北京市", null, null, null, null, null);
        PartnerTypicalCaseContract.SearchData response = new PartnerTypicalCaseContract.SearchData(
                "retrieval-1", "analysis-1", 1, "fact_similarity + 0.3 * issue_score",
                objectMapper.createObjectNode(), List.of(candidate), objectMapper.createObjectNode(),
                objectMapper.createObjectNode());
        when(mapper.upsertPartnerTypicalCase(eq(candidate), eq("partner:core_typical:18"),
                anyString(), anyString())).thenReturn(81L);
        doAnswer(invocation -> {
            invocation.<RecommendationWrite>getArgument(0).setRecommendId(91L);
            return 1;
        }).when(mapper).insertRecommendation(any(RecommendationWrite.class));

        long recommendId = service.savePartnerRecommendation(
                7L, 9L, null, "案件事实", filters,
                List.of(new AnalysisTokenService.TokenIssue(0, "合同效力", 0.9, 0.8)),
                response, 120, "request-1", "http-1");

        assertEquals(91L, recommendId);
        verify(mapper).upsertPartnerTypicalCaseContent(81L, "案例正文");
        ArgumentCaptor<RecommendationWrite> writeCaptor = ArgumentCaptor.forClass(RecommendationWrite.class);
        verify(mapper).insertRecommendation(writeCaptor.capture());
        assertNull(writeCaptor.getValue().getQueryEmbedding());
        verify(mapper).insertRecommendationItem(
                eq(91L), eq(9L), eq(81L), eq(1), eq(new BigDecimal("0.88")), eq(new BigDecimal("1.09")),
                contains("issueDetails"));
    }
}
