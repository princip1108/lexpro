package com.lexpro.lexprobackend.auth.config;

import com.lexpro.lexprobackend.common.web.HealthController;
import com.lexpro.lexprobackend.user.service.AppUserService;
import com.lexpro.lexprobackend.user.service.UserAdministrationService;
import com.lexpro.lexprobackend.user.web.UserController;
import com.lexpro.lexprobackend.common.web.dto.PageRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest({HealthController.class, UserController.class})
@Import({SecurityConfig.class, SecurityProblemWriter.class, JwtProperties.class})
@TestPropertySource(properties = {
        "lexpro.jwt.secret=test-only-secret-that-is-at-least-32-bytes-long",
        "lexpro.jwt.issuer=https://lexpro.local",
        "lexpro.jwt.access-token-ttl=PT30M"
})
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserService appUserService;

    @MockitoBean
    private UserAdministrationService userAdministrationService;

    @Test
    void shouldKeepHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldReturnProblemDetailForProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("X-Request-Id", "security-test-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(header().string("X-Request-Id", "security-test-123"))
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").value("security-test-123"));
    }

    @Test
    void shouldReturnProblemDetailWhenAuthenticatedUserLacksPermission() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void shouldAllowUserManagementPermission() throws Exception {
        when(appUserService.listUsers(any(PageRequest.class))).thenReturn(
                new PageResponse<>(List.of(), 1, 20, 0, 0)
        );

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(() -> "USER_MANAGE")))
                .andExpect(status().isOk());
    }
}
