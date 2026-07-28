package com.lexpro.lexprobackend.auth.config;

import com.lexpro.lexprobackend.common.web.HealthController;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
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
}
