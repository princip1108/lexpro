package com.lexpro.lexprobackend.processing.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.processing.service.LegalElementService;
import com.lexpro.lexprobackend.processing.web.dto.LegalElementJobResponse;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LegalElementController.class)
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class LegalElementControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LegalElementService service;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldAcceptLegalElementJob() throws Exception {
        when(service.start(eq(7L), eq(9L), eq(12L), anyString())).thenReturn(new LegalElementJobResponse(
                "168b22b3-f6dc-4aa3-8589-49518ad6a529", 12L, "PROCESSING", null, null,
                OffsetDateTime.now(), null));

        mockMvc.perform(post("/api/v1/cases/9/documents/12/legal-element-jobs")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("AI_EXECUTE"))))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/cases/9/documents/12/legal-element-jobs/"
                        + "168b22b3-f6dc-4aa3-8589-49518ad6a529"))
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void shouldRequireAiPermissionToStart() throws Exception {
        mockMvc.perform(post("/api/v1/cases/9/documents/12/legal-element-jobs")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_READ"))))
                .andExpect(status().isForbidden());
    }
}
