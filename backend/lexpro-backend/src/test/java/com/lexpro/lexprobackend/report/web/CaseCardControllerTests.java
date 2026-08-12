package com.lexpro.lexprobackend.report.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.report.service.CaseCardService;
import com.lexpro.lexprobackend.report.web.dto.CaseCardJobResponse;
import com.lexpro.lexprobackend.report.web.dto.StartCaseCardRequest;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseCardController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class CaseCardControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseCardService service;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldAcceptCaseCardJob() throws Exception {
        when(service.start(eq(7L), eq(9L), any(StartCaseCardRequest.class), anyString()))
                .thenReturn(new CaseCardJobResponse("168b22b3-f6dc-4aa3-8589-49518ad6a529",
                        40L, "AUTO", "PROCESSING", null, OffsetDateTime.now(), null));

        mockMvc.perform(post("/api/v1/cases/9/case-card-jobs")
                        .contentType("application/json")
                        .content("""
                                {"fillMode":"AUTO","sources":[{"sourceType":"DOCUMENT","sourceId":12}]}
                                """)
                        .with(jwt().jwt(token -> token.subject("7")).authorities(
                                new SimpleGrantedAuthority("REPORT_MANAGE"),
                                new SimpleGrantedAuthority("AI_EXECUTE"))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/cases/9/case-card-jobs/"
                        + "168b22b3-f6dc-4aa3-8589-49518ad6a529"))
                .andExpect(jsonPath("$.fillTaskId").value(40));
    }

    @Test
    void shouldValidateTypedSourceBeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/cases/9/case-card-jobs")
                        .contentType("application/json")
                        .content("""
                                {"fillMode":"AUTO","sources":[{"sourceType":"UNKNOWN","sourceId":12}]}
                                """)
                        .with(jwt().jwt(token -> token.subject("7")).authorities(
                                new SimpleGrantedAuthority("REPORT_MANAGE"),
                                new SimpleGrantedAuthority("AI_EXECUTE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
