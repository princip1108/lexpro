package com.lexpro.lexprobackend.auth.web.dto;

import com.lexpro.lexprobackend.auth.domain.AuthRole;

import java.util.List;

public record RoleResponse(
        Long roleId,
        String code,
        String name,
        String description,
        boolean system,
        String status,
        List<String> permissions
) {

    public RoleResponse {
        permissions = List.copyOf(permissions);
    }

    public static RoleResponse from(AuthRole role, List<String> permissions) {
        return new RoleResponse(
                role.getRoleId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                Boolean.TRUE.equals(role.getIsSystem()),
                role.getStatus(),
                permissions
        );
    }
}
