package com.lexpro.lexprobackend.processing.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.processing.service.EntityRecognitionService;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionResultResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EntityRecognitionController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class EntityRecognitionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EntityRecognitionService service;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldAcceptEntityRecognitionJob() throws Exception {
        when(service.start(eq(7L), eq(9L), eq(12L), anyString())).thenReturn(new EntityRecognitionJobResponse(
                "168b22b3-f6dc-4aa3-8589-49518ad6a529", 12L, "PROCESSING", null, null,
                OffsetDateTime.now(), null));

        mockMvc.perform(post("/api/v1/cases/9/documents/12/entity-recognition-jobs")
                        .header("X-Request-Id", "http-request-1")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("AI_EXECUTE"))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/cases/9/documents/12/entity-recognition-jobs/"
                        + "168b22b3-f6dc-4aa3-8589-49518ad6a529"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void shouldRequireAiPermissionToConfirm() throws Exception {
        mockMvc.perform(put("/api/v1/cases/9/documents/12/entity-results/30/confirmation")
                        .contentType("application/json")
                        .content("{\"finalEntities\":{\"entities\":[]}}")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturnConfirmedEntitiesWithoutPromptSnapshot() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        EntityRecognitionResultResponse response = new EntityRecognitionResultResponse(
                30L, 12L, 9L, objectMapper.readTree("{\"entities\":[]}"),
                objectMapper.readTree("{\"entities\":[]}"), "deepseek-v4-flash", null,
                "entity-recognition-v1", "entity-result-v1", objectMapper.readTree("{}"), null,
                "job-1", 100, 7L, OffsetDateTime.now(), 7L, OffsetDateTime.now());
        when(service.confirm(eq(7L), eq(9L), eq(12L), eq(30L), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/cases/9/documents/12/entity-results/30/confirmation")
                        .contentType("application/json")
                        .content("{\"finalEntities\":{\"entities\":[]}}")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("AI_EXECUTE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalEntities.entities").isArray())
                .andExpect(jsonPath("$.promptSnapshot").doesNotExist());
    }
}
