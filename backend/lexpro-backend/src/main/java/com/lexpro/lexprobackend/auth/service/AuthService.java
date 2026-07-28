package com.lexpro.lexprobackend.auth.service;

import com.lexpro.lexprobackend.auth.password.PasswordService;
import com.lexpro.lexprobackend.auth.token.JwtService;
import com.lexpro.lexprobackend.auth.web.dto.CurrentUserResponse;
import com.lexpro.lexprobackend.auth.web.dto.LoginRequest;
import com.lexpro.lexprobackend.auth.web.dto.LoginResponse;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.service.AppUserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private final AppUserService appUserService;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final String dummyPasswordHash;

    public AuthService(
            AppUserService appUserService,
            PasswordService passwordService,
            JwtService jwtService,
            AuditService auditService
    ) {
        this.appUserService = appUserService;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.dummyPasswordHash = passwordService.encode("DummyCredential1!NotAnAccount");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        Optional<UserAccount> optionalAccount = appUserService.findAccountForAuthentication(username);
        UserAccount account = optionalAccount.orElse(null);
        String passwordHash = account == null ? dummyPasswordHash : account.getPasswordHash();
        boolean passwordMatches = passwordService.matches(request.password(), passwordHash);

        if (account == null || !passwordMatches || !"ACTIVE".equals(account.getStatus())) {
            auditService.recordIndependent(new AuditEvent(
                    account == null ? null : account.getUserId(),
                    null,
                    "LOGIN_FAILED",
                    "APP_USER",
                    account == null ? null : String.valueOf(account.getUserId()),
                    AuditResult.FAILED,
                    Map.of("username", username)
            ));
            throw invalidCredentials();
        }

        List<String> permissions = appUserService.findPermissionCodes(account.getRoleId());
        JwtService.IssuedToken token = jwtService.issue(account, permissions);
        auditService.record(new AuditEvent(
                account.getUserId(),
                null,
                "LOGIN_SUCCEEDED",
                "APP_USER",
                String.valueOf(account.getUserId()),
                AuditResult.SUCCESS,
                Map.of()
        ));
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresAt(),
                CurrentUserResponse.from(account, permissions)
        );
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(long userId) {
        UserAccount account = appUserService.findAccountById(userId);
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Account disabled",
                    "ACCOUNT_DISABLED",
                    "The current account is disabled"
            );
        }
        return CurrentUserResponse.from(account, appUserService.findPermissionCodes(account.getRoleId()));
    }

    @Transactional
    public void logout(long userId, String tokenId) {
        auditService.record(new AuditEvent(
                userId,
                null,
                "LOGOUT",
                "APP_USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                tokenId == null ? Map.of() : Map.of("tokenId", tokenId)
        ));
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                "INVALID_CREDENTIALS",
                "The username or password is invalid"
        );
    }
}
