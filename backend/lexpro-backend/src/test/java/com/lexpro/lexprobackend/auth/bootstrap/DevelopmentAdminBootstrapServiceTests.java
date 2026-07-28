package com.lexpro.lexprobackend.auth.bootstrap;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.auth.domain.AuthRole;
import com.lexpro.lexprobackend.auth.mapper.AuthRoleMapper;
import com.lexpro.lexprobackend.auth.password.PasswordService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import com.lexpro.lexprobackend.organization.mapper.OrganizationUnitMapper;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentAdminBootstrapServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateFirstAdministratorWithBcryptAndAudit() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        OrganizationUnitMapper organizationMapper = mock(OrganizationUnitMapper.class);
        AuthRoleMapper roleMapper = mock(AuthRoleMapper.class);
        AuditService auditService = mock(AuditService.class);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(organizationMapper.selectByCode("LEXPRO")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            OrganizationUnit organization = invocation.getArgument(0);
            organization.setOrganizationId(10L);
            return 1;
        }).when(organizationMapper).insert(any(OrganizationUnit.class));
        AuthRole role = new AuthRole();
        role.setRoleId(20L);
        role.setRoleCode("ADMIN");
        when(roleMapper.selectOne(any(Wrapper.class))).thenReturn(role);
        doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setUserId(30L);
            return 1;
        }).when(userMapper).insert(any(AppUser.class));
        PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder(4));
        DevelopmentAdminBootstrapService service = new DevelopmentAdminBootstrapService(
                userMapper,
                organizationMapper,
                roleMapper,
                passwordService,
                auditService
        );

        DevelopmentAdminBootstrapService.BootstrapResult result = service.bootstrap(request());

        assertTrue(result.created());
        assertEquals(30L, result.userId());
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userMapper).insert(userCaptor.capture());
        AppUser inserted = userCaptor.getValue();
        assertEquals("admin", inserted.getUsername());
        assertNotEquals("StrongPass1!", inserted.getPasswordHash());
        assertTrue(passwordService.matches("StrongPass1!", inserted.getPasswordHash()));
        assertEquals(10L, inserted.getOrganizationId());
        assertEquals(20L, inserted.getRoleId());
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals("USER_BOOTSTRAPPED", auditCaptor.getValue().operationType());
        assertFalse(new ObjectMapper().valueToTree(auditCaptor.getValue()).toString().contains("StrongPass1!"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipWithoutReadingCredentialsWhenUsersExist() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        OrganizationUnitMapper organizationMapper = mock(OrganizationUnitMapper.class);
        AuthRoleMapper roleMapper = mock(AuthRoleMapper.class);
        AuditService auditService = mock(AuditService.class);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        DevelopmentAdminBootstrapService service = new DevelopmentAdminBootstrapService(
                userMapper,
                organizationMapper,
                roleMapper,
                new PasswordService(new BCryptPasswordEncoder(4)),
                auditService
        );

        DevelopmentAdminBootstrapService.BootstrapResult result = service.bootstrap(
                new DevelopmentAdminBootstrapService.BootstrapRequest(null, null, null, null, null)
        );

        assertFalse(result.created());
        verify(organizationMapper, never()).insert(any(OrganizationUnit.class));
        verify(userMapper, never()).insert(any(AppUser.class));
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    private DevelopmentAdminBootstrapService.BootstrapRequest request() {
        return new DevelopmentAdminBootstrapService.BootstrapRequest(
                "admin",
                "StrongPass1!",
                "System Administrator",
                "LEXPRO",
                "LexPro"
        );
    }
}
