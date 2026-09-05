package com.lexpro.lexprobackend.auth.web;

import com.lexpro.lexprobackend.auth.service.AuthService;
import com.lexpro.lexprobackend.auth.service.LoginRateLimiter;
import com.lexpro.lexprobackend.auth.web.dto.CurrentUserResponse;
import com.lexpro.lexprobackend.auth.web.dto.LoginRequest;
import com.lexpro.lexprobackend.auth.web.dto.LoginResponse;
import com.lexpro.lexprobackend.common.error.RateLimitException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private LoginRateLimiter loginRateLimiter;

    @MockitoBean
    private com.lexpro.lexprobackend.auth.service.CaptchaService captchas;

    @Test
    void shouldRejectMissingCaptchaBeforePasswordAuthentication() throws Exception {
        doThrow(new com.lexpro.lexprobackend.common.error.ApiException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid captcha", "CAPTCHA_INVALID", "验证码错误或已过期"))
                .when(captchas).verify(null, null);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CAPTCHA_INVALID"));
        org.mockito.Mockito.verifyNoInteractions(authService);
    }

    @Test
    void shouldReturnNoStoreLoginResponseWithoutPassword() throws Exception {
        CurrentUserResponse user = new CurrentUserResponse(
                1L,
                "admin",
                "System Administrator",
                null,
                new CurrentUserResponse.Role(2L, "ADMIN", "System administrator"),
                List.of("USER_MANAGE")
        );
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse(
                "signed-token",
                "Bearer",
                Instant.parse("2026-07-29T01:30:00Z"),
                user
        ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"StrongPass1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void shouldValidateLoginBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectControlCharactersInUsername() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"user\\u0000name\",\"password\":\"StrongPass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void shouldReturnRetryAfterWhenLoginIsRateLimited() throws Exception {
        doThrow(new RateLimitException(60)).when(loginRateLimiter).acquire(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"StrongPass1!"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.errorCode").value("LOGIN_RATE_LIMITED"));
    }
}
