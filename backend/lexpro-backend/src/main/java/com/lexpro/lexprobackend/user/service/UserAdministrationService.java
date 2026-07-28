package com.lexpro.lexprobackend.user.service;

import com.lexpro.lexprobackend.auth.domain.AuthRole;
import com.lexpro.lexprobackend.auth.mapper.AuthRoleMapper;
import com.lexpro.lexprobackend.auth.password.PasswordService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import com.lexpro.lexprobackend.organization.mapper.OrganizationUnitMapper;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import com.lexpro.lexprobackend.user.web.dto.CreateUserRequest;
import com.lexpro.lexprobackend.user.web.dto.UserDetailResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserAdministrationService {

    private final AppUserMapper appUserMapper;
    private final OrganizationUnitMapper organizationUnitMapper;
    private final AuthRoleMapper authRoleMapper;
    private final PasswordService passwordService;
    private final AuditService auditService;

    public UserAdministrationService(
            AppUserMapper appUserMapper,
            OrganizationUnitMapper organizationUnitMapper,
            AuthRoleMapper authRoleMapper,
            PasswordService passwordService,
            AuditService auditService
    ) {
        this.appUserMapper = appUserMapper;
        this.organizationUnitMapper = organizationUnitMapper;
        this.authRoleMapper = authRoleMapper;
        this.passwordService = passwordService;
        this.auditService = auditService;
    }

    @Transactional
    public UserDetailResponse createUser(long actorUserId, CreateUserRequest request) {
        String username = request.username().trim();
        if (appUserMapper.selectAccountByUsername(username).isPresent()) {
            throw usernameConflict();
        }
        OrganizationUnit organization = requireActiveOrganization(request.organizationId());
        AuthRole role = requireActiveRole(request.roleId());

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(encodePassword(request.password()));
        user.setRealName(request.realName().trim());
        user.setStatus("ACTIVE");
        user.setOrganizationId(organization.getOrganizationId());
        user.setRoleId(role.getRoleId());
        try {
            appUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw usernameConflict();
        }
        auditService.record(new AuditEvent(
                actorUserId,
                null,
                "USER_CREATED",
                "APP_USER",
                String.valueOf(user.getUserId()),
                AuditResult.SUCCESS,
                Map.of(
                        "username", username,
                        "roleId", role.getRoleId(),
                        "organizationId", organization.getOrganizationId()
                )
        ));
        return loadDetail(user.getUserId());
    }

    @Transactional
    public UserDetailResponse updateStatus(long actorUserId, long userId, String requestedStatus) {
        String status = requestedStatus.trim();
        UserAccount account = requireUser(userId);
        if (actorUserId == userId && "DISABLED".equals(status)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Cannot disable current user",
                    "SELF_DISABLE_FORBIDDEN",
                    "An administrator cannot disable their own account"
            );
        }
        if (!status.equals(account.getStatus())) {
            AppUser update = new AppUser();
            update.setUserId(userId);
            update.setStatus(status);
            appUserMapper.updateById(update);
            auditService.record(new AuditEvent(
                    actorUserId,
                    null,
                    "USER_STATUS_CHANGED",
                    "APP_USER",
                    String.valueOf(userId),
                    AuditResult.SUCCESS,
                    Map.of("from", account.getStatus(), "to", status)
            ));
        }
        return loadDetail(userId);
    }

    @Transactional
    public void resetPassword(long actorUserId, long userId, String newPassword) {
        requireUser(userId);
        AppUser update = new AppUser();
        update.setUserId(userId);
        update.setPasswordHash(encodePassword(newPassword));
        appUserMapper.updateById(update);
        auditService.record(new AuditEvent(
                actorUserId,
                null,
                "USER_PASSWORD_RESET",
                "APP_USER",
                String.valueOf(userId),
                AuditResult.SUCCESS,
                Map.of()
        ));
    }

    private OrganizationUnit requireActiveOrganization(long organizationId) {
        OrganizationUnit organization = organizationUnitMapper.selectById(organizationId);
        if (organization == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid organization",
                    "ORGANIZATION_NOT_FOUND",
                    "The selected organization does not exist"
            );
        }
        if (!"ACTIVE".equals(organization.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Organization disabled",
                    "ORGANIZATION_DISABLED",
                    "A disabled organization cannot receive new users"
            );
        }
        return organization;
    }

    private AuthRole requireActiveRole(long roleId) {
        AuthRole role = authRoleMapper.selectById(roleId);
        if (role == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid role",
                    "ROLE_NOT_FOUND",
                    "The selected role does not exist"
            );
        }
        if (!"ACTIVE".equals(role.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Role disabled",
                    "ROLE_DISABLED",
                    "A disabled role cannot be assigned"
            );
        }
        return role;
    }

    private String encodePassword(String password) {
        try {
            return passwordService.encode(password);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Weak password",
                    "WEAK_PASSWORD",
                    exception.getMessage()
            );
        }
    }

    private UserAccount requireUser(long userId) {
        return appUserMapper.selectAccountById(userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "User not found",
                        "USER_NOT_FOUND",
                        "The requested user does not exist"
                ));
    }

    private UserDetailResponse loadDetail(long userId) {
        UserAccount account = requireUser(userId);
        return UserDetailResponse.from(account, appUserMapper.selectPermissionCodes(account.getRoleId()));
    }

    private ApiException usernameConflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "Username already exists",
                "USERNAME_EXISTS",
                "The username is already in use"
        );
    }
}
