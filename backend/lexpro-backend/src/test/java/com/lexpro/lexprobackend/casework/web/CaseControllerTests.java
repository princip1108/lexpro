package com.lexpro.lexprobackend.casework.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.casework.service.CaseService;
import com.lexpro.lexprobackend.casework.web.dto.CaseDetailResponse;
import com.lexpro.lexprobackend.casework.web.dto.CaseSummaryResponse;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class CaseControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldListVisibleCasesWithCaseReadPermission() throws Exception {
        CaseSummaryResponse summary = new CaseSummaryResponse(
                41L, "Test case", "LEX-2026-1", "CRIMINAL", null, null, null,
                "PENDING", LocalDate.of(2026, 7, 29), null, false, "Reviewer Zhang",
                OffsetDateTime.parse("2026-07-29T10:00:00+08:00")
        );
        when(caseService.listCases(anyLong(), any())).thenReturn(new PageResponse<>(List.of(summary), 1, 20, 1, 1));

        mockMvc.perform(get("/api/v1/cases").with(jwt()
                        .jwt(token -> token.subject("7"))
                        .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].caseId").value(41))
                .andExpect(jsonPath("$.items[0].handlerName").value("Reviewer Zhang"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void shouldCreateCaseWithCaseWritePermission() throws Exception {
        CaseDetailResponse detail = new CaseDetailResponse(
                41L, "Test case", "LEX-2026-1", "CRIMINAL", null, null, null,
                "PENDING", LocalDate.of(2026, 7, 29), null, false, 7L,
                OffsetDateTime.parse("2026-07-29T10:00:00+08:00"),
                OffsetDateTime.parse("2026-07-29T10:00:00+08:00")
        );
        when(caseService.createCase(anyLong(), any())).thenReturn(detail);

        mockMvc.perform(post("/api/v1/cases")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "Test case",
                                  "caseNo": "LEX-2026-1",
                                  "caseType": "CRIMINAL",
                                  "acceptDate": "2026-07-29"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/cases/41"))
                .andExpect(jsonPath("$.caseStatus").value("PENDING"));

        ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
        verify(caseService).createCase(userCaptor.capture(), any());
        assertEquals(7L, userCaptor.getValue());
    }

    @Test
    void shouldRejectCaseCreateWithoutWritePermission() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "Test case",
                                  "caseType": "CRIMINAL"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldValidateCreateCaseRequest() throws Exception {
        mockMvc.perform(post("/api/v1/cases")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_WRITE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseName": "",
                                  "caseType": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.caseName").exists())
                .andExpect(jsonPath("$.fieldErrors.caseType").exists());
    }
}
