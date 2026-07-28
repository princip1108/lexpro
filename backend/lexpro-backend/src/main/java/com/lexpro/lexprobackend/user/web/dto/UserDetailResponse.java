package com.lexpro.lexprobackend.user.web.dto;

import com.lexpro.lexprobackend.user.domain.UserAccount;

import java.time.OffsetDateTime;
import java.util.List;

public record UserDetailResponse(
        Long userId,
        String username,
        String realName,
        String status,
        OrganizationSummary organization,
        RoleSummary role,
        List<String> permissions,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public UserDetailResponse {
        permissions = List.copyOf(permissions);
    }

    public static UserDetailResponse from(UserAccount account, List<String> permissions) {
        OrganizationSummary organization = account.getOrganizationId() == null
                ? null
                : new OrganizationSummary(
                        account.getOrganizationId(),
                        account.getOrganizationCode(),
                        account.getOrganizationName()
                );
        return new UserDetailResponse(
                account.getUserId(),
                account.getUsername(),
                account.getRealName(),
                account.getStatus(),
                organization,
                new RoleSummary(account.getRoleId(), account.getRoleCode(), account.getRoleName()),
                permissions,
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    public record OrganizationSummary(Long organizationId, String code, String name) {
    }

    public record RoleSummary(Long roleId, String code, String name) {
    }
}
