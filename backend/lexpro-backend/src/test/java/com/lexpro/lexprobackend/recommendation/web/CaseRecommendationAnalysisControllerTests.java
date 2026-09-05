package com.lexpro.lexprobackend.recommendation.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.recommendation.service.RecommendationService;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationAnalysisRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationAnalysisResponse;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseRecommendationAnalysisController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class CaseRecommendationAnalysisControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationService service;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldCreateBoundedPartnerAnalysisWithRequiredAuthorities() throws Exception {
        when(service.analyze(eq(7L), eq(9L), any(CreateRecommendationAnalysisRequest.class), anyString()))
                .thenReturn(new RecommendationAnalysisResponse(
                        "signed-token", Instant.parse("2026-08-14T01:10:00Z"), 2, 1,
                        new RecommendationAnalysisResponse.Timings(10, 20, 30),
                        List.of(new RecommendationAnalysisResponse.Issue(
                                0, "合同效力", 0.9, 0.8, 1, "双方签订合同。"))));

        mockMvc.perform(post("/api/v1/cases/9/recommendation-analyses")
                        .contentType("application/json")
                        .content("{\"factText\":\"双方签订合同并发生履行争议。\"}")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(
                                new SimpleGrantedAuthority("RECOMMENDATION_USE"),
                                new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/cases/9/recommendation-analyses"))
                .andExpect(jsonPath("$.analysisToken").value("signed-token"))
                .andExpect(jsonPath("$.issues[0].text").value("合同效力"));
    }

    @Test
    void shouldRequireExactlyOneFactSource() throws Exception {
        mockMvc.perform(post("/api/v1/cases/9/recommendation-analyses")
                        .contentType("application/json")
                        .content("{\"sourceSummaryId\":3,\"factText\":\"重复来源\"}")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(
                                new SimpleGrantedAuthority("RECOMMENDATION_USE"),
                                new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
