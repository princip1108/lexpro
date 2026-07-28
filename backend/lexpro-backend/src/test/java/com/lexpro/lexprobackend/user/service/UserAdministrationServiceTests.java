package com.lexpro.lexprobackend.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.auth.domain.AuthRole;
import com.lexpro.lexprobackend.auth.mapper.AuthRoleMapper;
import com.lexpro.lexprobackend.auth.password.PasswordService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import com.lexpro.lexprobackend.organization.mapper.OrganizationUnitMapper;
import com.lexpro.lexprobackend.user.domain.AppUser;
import com.lexpro.lexprobackend.user.domain.UserAccount;
import com.lexpro.lexprobackend.user.mapper.AppUserMapper;
import com.lexpro.lexprobackend.user.web.dto.CreateUserRequest;
import com.lexpro.lexprobackend.user.web.dto.UserDetailResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserAdministrationServiceTests {

    @Test
    void shouldCreateUserWithBcryptAndAuditWithoutPassword() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        OrganizationUnitMapper organizationMapper = mock(OrganizationUnitMapper.class);
        AuthRoleMapper roleMapper = mock(AuthRoleMapper.class);
        AuditService auditService = mock(AuditService.class);
        PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder(4));
        when(userMapper.selectAccountByUsername("new.user")).thenReturn(Optional.empty());
        OrganizationUnit organization = new OrganizationUnit();
        organization.setOrganizationId(10L);
        organization.setStatus("ACTIVE");
        when(organizationMapper.selectById(10L)).thenReturn(organization);
        AuthRole role = new AuthRole();
        role.setRoleId(20L);
        role.setStatus("ACTIVE");
        when(roleMapper.selectById(20L)).thenReturn(role);
        doAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            user.setUserId(30L);
            return 1;
        }).when(userMapper).insert(any(AppUser.class));
        UserAccount created = account(30L, "new.user", "ACTIVE");
        created.setOrganizationId(10L);
        created.setRoleId(20L);
        created.setRoleCode("USER");
        when(userMapper.selectAccountById(30L)).thenReturn(Optional.of(created));
        when(userMapper.selectPermissionCodes(20L)).thenReturn(List.of("CASE_READ"));
        UserAdministrationService service = new UserAdministrationService(
                userMapper,
                organizationMapper,
                roleMapper,
                passwordService,
                auditService
        );

        UserDetailResponse response = service.createUser(
                1L,
                new CreateUserRequest(" new.user ", "StrongPass1!", "New User", 10L, 20L)
        );

        assertEquals(30L, response.userId());
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userMapper).insert(userCaptor.capture());
        assertTrue(passwordService.matches("StrongPass1!", userCaptor.getValue().getPasswordHash()));
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals("USER_CREATED", auditCaptor.getValue().operationType());
        String auditJson = new ObjectMapper().valueToTree(auditCaptor.getValue()).toString();
        assertTrue(!auditJson.contains("StrongPass1!") && !auditJson.contains("password"));
    }

    @Test
    void shouldPreventAdministratorFromDisablingSelf() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        UserAccount account = account(1L, "admin", "ACTIVE");
        when(userMapper.selectAccountById(1L)).thenReturn(Optional.of(account));
        AuditService auditService = mock(AuditService.class);
        UserAdministrationService service = new UserAdministrationService(
                userMapper,
                mock(OrganizationUnitMapper.class),
                mock(AuthRoleMapper.class),
                new PasswordService(new BCryptPasswordEncoder(4)),
                auditService
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.updateStatus(1L, 1L, "DISABLED")
        );

        assertEquals("SELF_DISABLE_FORBIDDEN", exception.getErrorCode());
        verify(userMapper, never()).updateById(any(AppUser.class));
        verify(auditService, never()).record(any(AuditEvent.class));
    }

    private UserAccount account(long id, String username, String status) {
        UserAccount account = new UserAccount();
        account.setUserId(id);
        account.setUsername(username);
        account.setRealName(username);
        account.setStatus(status);
        return account;
    }
}
