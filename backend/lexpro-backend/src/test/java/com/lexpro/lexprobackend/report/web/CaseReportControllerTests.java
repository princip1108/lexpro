package com.lexpro.lexprobackend.report.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.report.service.CaseReportService;
import com.lexpro.lexprobackend.report.service.ExportedReport;
import com.lexpro.lexprobackend.report.service.ReportExportService;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseReportController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class CaseReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseReportService service;

    @MockitoBean
    private ReportExportService exportService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldReturnProtectedDownloadResponse() throws Exception {
        when(exportService.export(7L, 9L, 40L, "DOCX"))
                .thenReturn(new ExportedReport(new byte[]{1, 2, 3},
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "case-9-REVIEW_REPORT-v2.docx"));

        mockMvc.perform(get("/api/v1/cases/9/reports/40/exports/DOCX")
                        .with(jwt().jwt(token -> token.subject("7")).authorities(
                                new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment;")));
    }

    @Test
    void shouldValidateReviewReturnReasonBeforeService() throws Exception {
        mockMvc.perform(put("/api/v1/cases/9/reports/40/review-return")
                        .contentType("application/json")
                        .content("""
                                {"lockVersion":3,"reason":" "}
                                """)
                        .with(jwt().jwt(token -> token.subject("7")).authorities(
                                new SimpleGrantedAuthority("REPORT_MANAGE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
