package com.lexpro.lexprobackend.auth.web.dto;

import com.lexpro.lexprobackend.user.domain.UserAccount;

import java.util.List;

public record CurrentUserResponse(
        Long userId,
        String username,
        String realName,
        Organization organization,
        Role role,
        List<String> permissions
) {

    public CurrentUserResponse {
        permissions = List.copyOf(permissions);
    }

    public static CurrentUserResponse from(UserAccount account, List<String> permissions) {
        Organization organization = account.getOrganizationId() == null
                ? null
                : new Organization(
                        account.getOrganizationId(),
                        account.getOrganizationCode(),
                        account.getOrganizationName()
                );
        return new CurrentUserResponse(
                account.getUserId(),
                account.getUsername(),
                account.getRealName(),
                organization,
                new Role(account.getRoleId(), account.getRoleCode(), account.getRoleName()),
                permissions
        );
    }

    public record Organization(Long organizationId, String code, String name) {
    }

    public record Role(Long roleId, String code, String name) {
    }
}
