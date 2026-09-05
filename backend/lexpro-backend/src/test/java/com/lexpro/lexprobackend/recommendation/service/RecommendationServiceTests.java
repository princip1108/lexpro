package com.lexpro.lexprobackend.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.recommendation.client.RetrievalClient;
import com.lexpro.lexprobackend.recommendation.client.RetrievalContract;
import com.lexpro.lexprobackend.recommendation.config.RetrievalProperties;
import com.lexpro.lexprobackend.recommendation.config.PartnerTypicalCaseProperties;
import com.lexpro.lexprobackend.recommendation.config.TypicalCaseProvider;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationItemRecord;
import com.lexpro.lexprobackend.recommendation.domain.RecommendationRecord;
import com.lexpro.lexprobackend.recommendation.mapper.RecommendationMapper;
import com.lexpro.lexprobackend.recommendation.partner.AnalysisTokenService;
import com.lexpro.lexprobackend.recommendation.partner.PartnerTypicalCaseClient;
import com.lexpro.lexprobackend.recommendation.partner.PartnerTypicalCaseContract;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationAnalysisRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.PartnerRecommendationFiltersRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Instant;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecommendationServiceTests {

    @Test
    void shouldUseStoredSummaryForPartnerAnalysisAndTokenBinding() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RecommendationPersistenceService persistence = mock(RecommendationPersistenceService.class);
        RetrievalClient localClient = mock(RetrievalClient.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        AuditService auditService = mock(AuditService.class);
        PartnerTypicalCaseClient partnerClient = mock(PartnerTypicalCaseClient.class);
        AnalysisTokenService tokenService = mock(AnalysisTokenService.class);
        RetrievalProperties properties = properties();
        properties.setProvider(TypicalCaseProvider.PARTNER);
        PartnerTypicalCaseProperties partnerProperties = new PartnerTypicalCaseProperties();
        partnerProperties.setAllowCaseData(true);
        String summaryText = "行为人使用虚假材料收取预付款，并将大部分款项用于个人债务和消费。";
        when(mapper.selectSummaryText(9L, 55L)).thenReturn(summaryText);
        when(partnerClient.analyze(any())).thenReturn(new PartnerTypicalCaseContract.AnalyzeEnvelope(
                0, "success", new PartnerTypicalCaseContract.AnalyzeData(
                "analysis-1", 1, 1, 10.0, 20.0, 30.0,
                List.of(new PartnerTypicalCaseContract.AnalyzeIssue(
                        0, "非法占有目的的认定", 0.9, 0.8, 0, summaryText)))));
        when(tokenService.issue(eq("analysis-1"), eq(7L), eq(9L), eq(summaryText), any()))
                .thenReturn(new AnalysisTokenService.IssuedToken(
                        "signed-analysis", Instant.parse("2026-08-15T02:00:00Z")));
        RecommendationService service = new RecommendationService(
                mapper, persistence, localClient, properties, partnerProperties, partnerClient, tokenService,
                accessService, auditService, new ObjectMapper());

        var result = service.analyze(7L, 9L,
                new CreateRecommendationAnalysisRequest(55L, null), "http-1");

        assertEquals("signed-analysis", result.analysisToken());
        assertEquals("非法占有目的的认定", result.issues().getFirst().text());
        ArgumentCaptor<PartnerTypicalCaseContract.AnalyzeRequest> request =
                ArgumentCaptor.forClass(PartnerTypicalCaseContract.AnalyzeRequest.class);
        verify(partnerClient).analyze(request.capture());
        assertEquals(summaryText, request.getValue().caseFact());
        verify(tokenService).issue(eq("analysis-1"), eq(7L), eq(9L), eq(summaryText), eq(List.of(
                new AnalysisTokenService.TokenIssue(0, "非法占有目的的认定", 0.9, 0.8))));
        verify(accessService).requireRead(9L, 7L);
    }

    @Test
    void shouldAuthorizeRetrievePersistAndReturnRankedResult() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RecommendationPersistenceService persistence = mock(RecommendationPersistenceService.class);
        RetrievalClient client = mock(RetrievalClient.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        AuditService auditService = mock(AuditService.class);
        PartnerTypicalCaseProperties partnerProperties = new PartnerTypicalCaseProperties();
        PartnerTypicalCaseClient partnerClient = mock(PartnerTypicalCaseClient.class);
        AnalysisTokenService tokenService = mock(AnalysisTokenService.class);
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
                40L, 8L, 1, new BigDecimal("0.910000"), null, "[]", "ext-8", null, "典型案例",
                "买卖合同纠纷", "[]", null, null, "某法院", "中级", null, "[]", null,
                LocalDate.of(2025, 1, 1), false)));
        RecommendationService service = new RecommendationService(
                mapper, persistence, client, properties, partnerProperties, partnerClient, tokenService,
                accessService, auditService, new ObjectMapper());

        var result = service.recommend(7L, 9L,
                new CreateRecommendationRequest(
                        null, "合同货款争议", List.of("货款支付"), null, 10, null, null), "http-1");

        assertEquals(30L, result.recommendId());
        assertEquals(8L, result.items().getFirst().typicalCaseId());
        verify(accessService).requireRead(9L, 7L);
        verify(persistence).saveRecommendation(eq(7L), eq(9L), isNull(), eq("合同货款争议"),
                eq(List.of("货款支付")), any(), any(), anyInt(), eq("http-1"));
    }

    @Test
    void shouldVerifyAnalysisBindingAndPersistPartnerRecommendation() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RecommendationPersistenceService persistence = mock(RecommendationPersistenceService.class);
        RetrievalClient localClient = mock(RetrievalClient.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        AuditService auditService = mock(AuditService.class);
        PartnerTypicalCaseClient partnerClient = mock(PartnerTypicalCaseClient.class);
        AnalysisTokenService tokenService = mock(AnalysisTokenService.class);
        RetrievalProperties properties = properties();
        properties.setProvider(TypicalCaseProvider.PARTNER);
        PartnerTypicalCaseProperties partnerProperties = new PartnerTypicalCaseProperties();
        partnerProperties.setAllowCaseData(true);
        partnerProperties.setMaxResults(20);
        List<AnalysisTokenService.TokenIssue> issues = List.of(
                new AnalysisTokenService.TokenIssue(0, "合同效力", 0.9, 0.9));
        when(tokenService.verify("signed-analysis", 7L, 9L, "合同货款争议"))
                .thenReturn(new AnalysisTokenService.TokenPayload(
                        "partner-v1", "provider-analysis-1", 7L, 9L, "hash", issues, Long.MAX_VALUE));
        ObjectMapper objectMapper = new ObjectMapper();
        PartnerTypicalCaseContract.Candidate candidate = new PartnerTypicalCaseContract.Candidate(
                18L, "案例标题", "（2025）京01号", null, "合同纠纷", null,
                null, "民事", "北京市", "某法院", "中级法院", LocalDate.of(2025, 1, 2),
                "一审", "判决书", 0.12, 0.88, "core_typical", "content_typical",
                1, 1, -0.2, 0.45, 1.015, List.of(), "案例正文");
        PartnerTypicalCaseContract.SearchData partnerData = new PartnerTypicalCaseContract.SearchData(
                "retrieval-1", "provider-analysis-1", 1,
                "fact_similarity + 0.3 * issue_score", objectMapper.createObjectNode(), List.of(candidate),
                objectMapper.createObjectNode(), objectMapper.createObjectNode());
        when(partnerClient.search(any())).thenReturn(
                new PartnerTypicalCaseContract.SearchEnvelope(0, "success", partnerData));
        when(persistence.savePartnerRecommendation(
                anyLong(), anyLong(), any(), any(), any(), any(), any(), anyInt(), any(), any()))
                .thenReturn(31L);
        OffsetDateTime now = OffsetDateTime.now();
        when(mapper.selectRecommendation(9L, 31L)).thenReturn(new RecommendationRecord(
                31L, 9L, null, "合同货款争议", "[]", "partner-typical-case-service", "partner-v1",
                "partner-search-v1", "{\"provider\":\"PARTNER\",\"providerAnalysisId\":\"secret-analysis\","
                + "\"providerRetrievalId\":\"secret-retrieval\",\"rankingRule\":\"rule\"}",
                "request-1", 12, 7L, now));
        when(mapper.selectRecommendationItems(9L, 31L, 7L)).thenReturn(List.of(new RecommendationItemRecord(
                41L, 81L, 1, new BigDecimal("0.880000"), new BigDecimal("1.090000"), "{}",
                "partner:core_typical:18", "（2025）京01号", "案例标题", "合同纠纷", "[]", "民事",
                "北京市", "某法院", "中级法院", "判决书", "[]", "典型案例",
                LocalDate.of(2025, 1, 2), false)));
        RecommendationService service = new RecommendationService(
                mapper, persistence, localClient, properties, partnerProperties, partnerClient, tokenService,
                accessService, auditService, objectMapper);

        var result = service.recommend(7L, 9L, new CreateRecommendationRequest(
                null, "合同货款争议", null, null, 10, "signed-analysis",
                new PartnerRecommendationFiltersRequest(
                        null, null, null, null, null, "北京市", null, null, null, null, null)), "http-1");

        assertEquals(31L, result.recommendId());
        assertEquals(new BigDecimal("1.090000"), result.items().getFirst().rankingScore());
        org.junit.jupiter.api.Assertions.assertNull(result.items().getFirst().externalCaseId());
        org.junit.jupiter.api.Assertions.assertFalse(result.parameters().has("providerAnalysisId"));
        org.junit.jupiter.api.Assertions.assertFalse(result.parameters().has("providerRetrievalId"));
        assertEquals("rule", result.parameters().path("rankingRule").textValue());
        verify(accessService).requireRead(9L, 7L);
        verify(tokenService).verify("signed-analysis", 7L, 9L, "合同货款争议");
        verify(partnerClient).search(any(PartnerTypicalCaseContract.SearchRequest.class));
        verify(persistence).savePartnerRecommendation(
                eq(7L), eq(9L), isNull(), eq("合同货款争议"), any(), eq(issues), eq(partnerData),
                anyInt(), any(), eq("http-1"));
    }

    @Test
    void shouldRunPartnerRecommendationForMcpWithoutPersistingCaseHistory() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        RecommendationPersistenceService persistence = mock(RecommendationPersistenceService.class);
        RetrievalClient localClient = mock(RetrievalClient.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        AuditService auditService = mock(AuditService.class);
        PartnerTypicalCaseClient partnerClient = mock(PartnerTypicalCaseClient.class);
        AnalysisTokenService tokenService = mock(AnalysisTokenService.class);
        RetrievalProperties properties = properties();
        properties.setProvider(TypicalCaseProvider.PARTNER);
        PartnerTypicalCaseProperties partnerProperties = new PartnerTypicalCaseProperties();
        partnerProperties.setAllowCaseData(true);
        partnerProperties.setMaxResults(20);
        ObjectMapper objectMapper = new ObjectMapper();
        String factText = "The parties signed a contract";
        when(partnerClient.analyze(any())).thenReturn(new PartnerTypicalCaseContract.AnalyzeEnvelope(
                0, "success", new PartnerTypicalCaseContract.AnalyzeData(
                "analysis-1", 2, 1, 10.0, 2.0, 12.0,
                List.of(new PartnerTypicalCaseContract.AnalyzeIssue(
                        0, "contract validity", 0.9, 0.8, 0, "The parties signed a contract")))));
        PartnerTypicalCaseContract.Candidate candidate = new PartnerTypicalCaseContract.Candidate(
                18L, " ", "CASE-2025-01", java.util.Arrays.asList(null, "Article 1"), "Contract dispute", null,
                null, "Civil", "Beijing", "People's Court", "Intermediate Court",
                LocalDate.of(2025, 1, 2), "First instance", "Judgment", 0.12, 0.88,
                "core_typical", "content_typical", 1, 1, -0.2, 0.45, 1.015,
                List.of(Map.of("reason", "fact similarity")), "Full case content");
        PartnerTypicalCaseContract.SearchData partnerData = new PartnerTypicalCaseContract.SearchData(
                "retrieval-1", "analysis-1", 1, "weighted rank", objectMapper.createObjectNode(),
                List.of(candidate), objectMapper.createObjectNode(), objectMapper.createObjectNode());
        when(partnerClient.search(any())).thenReturn(
                new PartnerTypicalCaseContract.SearchEnvelope(0, "success", partnerData));

        RecommendationService service = new RecommendationService(
                mapper, persistence, localClient, properties, partnerProperties, partnerClient, tokenService,
                accessService, auditService, objectMapper);

        RecommendationService.McpRecommendationResult result = service.recommendForMcp(
                factText, new PartnerRecommendationFiltersRequest(null, null, null, null, null,
                        "Beijing", null, null, null, null, null), 5);

        assertEquals("analysis-1", result.analysis().analysisId());
        assertEquals("retrieval-1", result.search().retrievalId());
        assertEquals(1, result.search().candidates().size());
        assertEquals("CASE-2025-01", result.search().candidates().getFirst().title());
        assertEquals(List.of("Article 1"), result.search().candidates().getFirst().applicableLaw());
        verify(partnerClient).analyze(any());
        verify(partnerClient).search(any());
        verifyNoInteractions(persistence, mapper, accessService, auditService, tokenService);
    }

    @Test
    void shouldApplyAllTypicalCaseLibraryFiltersIncludingOneExactDate() {
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        LocalDate judgmentDate = LocalDate.of(2025, 3, 6);
        when(mapper.countTypicalCases(7L, "合同", "合同纠纷", "民事", "北京市第一中级人民法院",
                "北京市", "判决书", "最高人民法院", "指导性案例",
                judgmentDate, judgmentDate.minusDays(1), judgmentDate.plusDays(1), false)).thenReturn(0L);
        RecommendationService service = new RecommendationService(
                mapper, mock(RecommendationPersistenceService.class), mock(RetrievalClient.class), properties(),
                new PartnerTypicalCaseProperties(), mock(PartnerTypicalCaseClient.class),
                mock(AnalysisTokenService.class), mock(CaseAccessService.class), mock(AuditService.class),
                new ObjectMapper());

        var result = service.typicalCases(7L, " 合同 ", "合同纠纷", "民事", "北京市第一中级人民法院",
                "北京市", "判决书", "最高人民法院", "指导性案例",
                judgmentDate, judgmentDate.minusDays(1), judgmentDate.plusDays(1), false, 1, 20);

        assertEquals(0, result.totalItems());
        verify(mapper).selectTypicalCases(7L, "合同", "合同纠纷", "民事", "北京市第一中级人民法院",
                "北京市", "判决书", "最高人民法院", "指导性案例",
                judgmentDate, judgmentDate.minusDays(1), judgmentDate.plusDays(1), false, 20, 0L);
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="LEXPRO_READONLY_DB_TEST", matches="true")
    void shouldMapMigratedTypicalCasesAgainstReadOnlyDevelopmentDatabase() throws Exception {
        try(var connection=java.sql.DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/lexpro?currentSchema=lexpro","postgres",System.getenv("LEXPRO_TEST_DB_PASSWORD"))) {
            connection.setReadOnly(true);
            var configuration=new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(RecommendationMapper.class);
            configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("readonly",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),new org.apache.ibatis.datasource.unpooled.UnpooledDataSource()));
            try(var session=new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration).openSession(connection)) {
                var mapper=session.getMapper(RecommendationMapper.class);
                var items=mapper.selectTypicalCases(1,null,null,null,null,null,null,null,null,null,null,null,false,20,0);
                org.junit.jupiter.api.Assertions.assertFalse(items.isEmpty());
                for(var item:items)org.junit.jupiter.api.Assertions.assertNotNull(mapper.selectTypicalCase(item.typicalCaseId(),1).title());
                long count=mapper.countTypicalCases(1,"案件",null,null,null,null,null,null,null,null,null,null,false);
                var filtered=mapper.selectTypicalCases(1,"案件",null,null,null,null,null,null,null,null,null,null,false,200,0);
                org.junit.jupiter.api.Assertions.assertEquals(count,filtered.size());
            }
        }
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
