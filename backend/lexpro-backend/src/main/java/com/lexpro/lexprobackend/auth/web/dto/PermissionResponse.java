package com.lexpro.lexprobackend.auth.web.dto;

import com.lexpro.lexprobackend.auth.domain.AuthPermission;

public record PermissionResponse(
        Long permissionId,
        String code,
        String name,
        String description
) {

    public static PermissionResponse from(AuthPermission permission) {
        return new PermissionResponse(
                permission.getPermissionId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getDescription()
        );
    }
}
