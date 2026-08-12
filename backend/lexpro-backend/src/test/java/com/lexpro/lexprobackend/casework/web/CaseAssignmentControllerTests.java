package com.lexpro.lexprobackend.casework.web;

import com.lexpro.lexprobackend.auth.config.JwtProperties;
import com.lexpro.lexprobackend.auth.config.SecurityConfig;
import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import com.lexpro.lexprobackend.casework.service.CaseAssignmentService;
import com.lexpro.lexprobackend.casework.web.dto.CaseAssignmentResponse;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseAssignmentController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class CaseAssignmentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CaseAssignmentService assignmentService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void shouldCreateAssignmentWithAssignPermission() throws Exception {
        CaseAssignmentResponse response = new CaseAssignmentResponse();
        response.setAssignmentId(88L);
        response.setCaseId(41L);
        response.setUserId(9L);
        response.setAssignmentRole("REVIEWER");
        response.setAccessLevel("EDIT");
        when(assignmentService.assign(anyLong(), anyLong(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/cases/41/assignments")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_ASSIGN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 9,
                                  "assignmentRole": "REVIEWER",
                                  "accessLevel": "EDIT"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/cases/41/assignments/88"))
                .andExpect(jsonPath("$.assignmentRole").value("REVIEWER"));
    }

    @Test
    void shouldRejectAssignmentWithoutAssignPermission() throws Exception {
        mockMvc.perform(post("/api/v1/cases/41/assignments")
                        .with(jwt().jwt(token -> token.subject("7"))
                                .authorities(new SimpleGrantedAuthority("CASE_READ")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 9,
                                  "assignmentRole": "REVIEWER",
                                  "accessLevel": "EDIT"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
