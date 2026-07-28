package com.lexpro.lexprobackend.user.web.dto;

import com.lexpro.lexprobackend.user.domain.AppUser;

import java.time.OffsetDateTime;

public record UserSummaryResponse(
        Long userId,
        String username,
        String realName,
        String status,
        Long organizationId,
        Long roleId,
        OffsetDateTime createdAt
) {

    public static UserSummaryResponse from(AppUser user) {
        return new UserSummaryResponse(
                user.getUserId(),
                user.getUsername(),
                user.getRealName(),
                user.getStatus(),
                user.getOrganizationId(),
                user.getRoleId(),
                user.getCreatedAt()
        );
    }
}
