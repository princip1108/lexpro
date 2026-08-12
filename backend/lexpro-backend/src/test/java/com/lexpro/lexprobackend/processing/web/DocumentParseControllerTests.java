package com.lexpro.lexprobackend.processing.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.processing.service.DocumentParseService;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseDetailResponse;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseSummaryResponse;
import com.lexpro.lexprobackend.processing.web.dto.StartDocumentParseRequest;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentParseController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class DocumentParseControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentParseService parseService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldAcceptAsyncParseRequestWithLocation() throws Exception {
        when(parseService.start(eq(7L), eq(9L), eq(5L), any(StartDocumentParseRequest.class),
                eq("parse-request-123"))).thenReturn(summary());

        mockMvc.perform(post("/api/v1/cases/9/dossier/files/5/parse-jobs")
                        .header("X-Request-Id", "parse-request-123")
                        .contentType("application/json")
                        .content("{}")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("AI_EXECUTE"))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/cases/9/documents/12"))
                .andExpect(jsonPath("$.parseStatus").value("PROCESSING"));
    }

    @Test
    void shouldRequireAiPermissionToStartParsing() throws Exception {
        mockMvc.perform(post("/api/v1/cases/9/dossier/files/5/parse-jobs")
                        .contentType("application/json")
                        .content("{}")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnParsedContentWithoutStorageLocation() throws Exception {
        when(parseService.detail(7L, 9L, 12L)).thenReturn(new DocumentParseDetailResponse(
                12L, 5L, 9L, 1, "SUCCESS", null, "parsed text", "local-text/1", null,
                OffsetDateTime.now(), OffsetDateTime.now(), 7L, "request-123", null, 10, true
        ));

        mockMvc.perform(get("/api/v1/cases/9/documents/12")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawText").value("parsed text"))
                .andExpect(jsonPath("$.fileUrl").doesNotExist())
                .andExpect(jsonPath("$.storagePath").doesNotExist());
    }

    private DocumentParseSummaryResponse summary() {
        return new DocumentParseSummaryResponse(12L, 5L, 9L, 1, "PROCESSING", null, null,
                OffsetDateTime.now(), null, 7L, "parse-request-123", null, true);
    }
}
