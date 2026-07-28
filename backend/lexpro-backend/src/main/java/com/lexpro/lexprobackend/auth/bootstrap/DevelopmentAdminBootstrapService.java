package com.lexpro.lexprobackend.auth.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lexpro.lexprobackend.auth.domain.AuthRole;
import com.lexpro.lexprobackend.auth.mapper.AuthRoleMapper;
import com.lexpro.lexprobackend.auth.password.PasswordService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import com.lexpro.lexprobackend.organization.mapper.OrganizationUnitMapper;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DevelopmentAdminBootstrapService {

    private final AppUserMapper appUserMapper;
    private final OrganizationUnitMapper organizationUnitMapper;
    private final AuthRoleMapper authRoleMapper;
    private final PasswordService passwordService;
    private final AuditService auditService;

    public DevelopmentAdminBootstrapService(
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
    public BootstrapResult bootstrap(BootstrapRequest request) {
        if (appUserMapper.selectCount(Wrappers.<AppUser>lambdaQuery()) != 0) {
            return new BootstrapResult(false, null);
        }
        validate(request);

        OrganizationUnit organization = organizationUnitMapper
                .selectByCode(request.organizationCode().trim())
                .orElse(null);
        if (organization == null) {
            organization = new OrganizationUnit();
            organization.setOrganizationCode(request.organizationCode().trim());
            organization.setOrganizationName(request.organizationName().trim());
            organization.setOrganizationType("ROOT");
            organization.setSortNo(0);
            organization.setStatus("ACTIVE");
            organizationUnitMapper.insert(organization);
        }

        AuthRole adminRole = authRoleMapper.selectOne(
                Wrappers.<AuthRole>lambdaQuery()
                        .eq(AuthRole::getRoleCode, "ADMIN")
                        .eq(AuthRole::getStatus, "ACTIVE")
        );
        if (adminRole == null) {
            throw new IllegalStateException("Active ADMIN role is missing from the V3 baseline");
        }

        AppUser admin = new AppUser();
        admin.setUsername(request.username().trim());
        admin.setPasswordHash(passwordService.encode(request.password()));
        admin.setRealName(request.realName().trim());
        admin.setStatus("ACTIVE");
        admin.setOrganizationId(organization.getOrganizationId());
        admin.setRoleId(adminRole.getRoleId());
        appUserMapper.insert(admin);

        auditService.record(new AuditEvent(
                admin.getUserId(),
                null,
                "USER_BOOTSTRAPPED",
                "APP_USER",
                String.valueOf(admin.getUserId()),
                AuditResult.SUCCESS,
                Map.of("username", admin.getUsername())
        ));
        return new BootstrapResult(true, admin.getUserId());
    }

    private void validate(BootstrapRequest request) {
        requireText(request.username(), "Bootstrap username is required", 100);
        requireText(request.realName(), "Bootstrap real name is required", 100);
        requireText(request.organizationCode(), "Bootstrap organization code is required", 100);
        requireText(request.organizationName(), "Bootstrap organization name is required", 255);
        passwordService.validate(request.password());
    }

    private void requireText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(message + " and must not exceed " + maxLength + " characters");
        }
    }

    public record BootstrapRequest(
            String username,
            String password,
            String realName,
            String organizationCode,
            String organizationName
    ) {
    }

    public record BootstrapResult(boolean created, Long userId) {
    }
}
