package com.lexpro.lexprobackend.auth.service;

import com.lexpro.lexprobackend.auth.password.PasswordService;
import com.lexpro.lexprobackend.auth.token.JwtService;
import com.lexpro.lexprobackend.auth.web.dto.LoginRequest;
import com.lexpro.lexprobackend.auth.web.dto.LoginResponse;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTests {

    private final PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder(4));

    @Test
    void shouldIssueTokenAndAuditSuccessfulLogin() {
        AppUserService userService = mock(AppUserService.class);
        JwtService jwtService = mock(JwtService.class);
        AuditService auditService = mock(AuditService.class);
        UserAccount account = account("ACTIVE");
        account.setPasswordHash(passwordService.encode("StrongPass1!"));
        when(userService.findAccountForAuthentication("admin")).thenReturn(Optional.of(account));
        when(userService.findPermissionCodes(2L)).thenReturn(List.of("USER_MANAGE"));
        Instant expiresAt = Instant.parse("2026-07-29T01:30:00Z");
        when(jwtService.issue(account, List.of("USER_MANAGE")))
                .thenReturn(new JwtService.IssuedToken("signed-token", expiresAt));
        AuthService service = new AuthService(userService, passwordService, jwtService, auditService);

        LoginResponse response = service.login(new LoginRequest(" admin ", "StrongPass1!"));

        assertEquals("signed-token", response.accessToken());
        assertEquals(expiresAt, response.expiresAt());
        assertEquals("ADMIN", response.user().role().code());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(captor.capture());
        assertEquals("LOGIN_SUCCEEDED", captor.getValue().operationType());
        assertEquals(AuditResult.SUCCESS, captor.getValue().result());
    }

    @Test
    void shouldReturnSameErrorAndAuditForUnknownOrDisabledAccount() {
        AppUserService userService = mock(AppUserService.class);
        JwtService jwtService = mock(JwtService.class);
        AuditService auditService = mock(AuditService.class);
        when(userService.findAccountForAuthentication("missing")).thenReturn(Optional.empty());
        AuthService service = new AuthService(userService, passwordService, jwtService, auditService);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.login(new LoginRequest("missing", "StrongPass1!"))
        );

        assertEquals("INVALID_CREDENTIALS", exception.getErrorCode());
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).recordIndependent(captor.capture());
        assertEquals("LOGIN_FAILED", captor.getValue().operationType());
        assertEquals(AuditResult.FAILED, captor.getValue().result());
    }

    private UserAccount account(String status) {
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setUsername("admin");
        account.setRealName("System Administrator");
        account.setStatus(status);
        account.setRoleId(2L);
        account.setRoleCode("ADMIN");
        account.setRoleName("System administrator");
        return account;
    }
}
