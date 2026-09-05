package com.lexpro.lexprobackend.auth.web;

import com.lexpro.lexprobackend.auth.service.AuthService;
import com.lexpro.lexprobackend.auth.service.LoginRateLimiter;
import com.lexpro.lexprobackend.auth.web.dto.CurrentUserResponse;
import com.lexpro.lexprobackend.auth.web.dto.LoginRequest;
import com.lexpro.lexprobackend.auth.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;
    private final com.lexpro.lexprobackend.auth.service.CaptchaService captchas;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter,
                          com.lexpro.lexprobackend.auth.service.CaptchaService captchas) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
        this.captchas = captchas;
    }

    @GetMapping("/captcha")
    public ResponseEntity<com.lexpro.lexprobackend.auth.service.CaptchaService.Challenge> captcha() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(captchas.create());
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and issue an access token")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        loginRateLimiter.acquire(request.username(), httpRequest.getRemoteAddr());
        captchas.verify(request.captchaId(), request.captcha());
        LoginResponse response = authService.login(request);
        loginRateLimiter.recordSuccess(request.username());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current authenticated user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.currentUser(Long.parseLong(jwt.getSubject())));
    }

    @PostMapping("/logout")
    @Operation(summary = "Record logout; the client must discard its access token")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(Long.parseLong(jwt.getSubject()), jwt.getId());
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
