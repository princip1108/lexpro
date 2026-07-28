package com.lexpro.lexprobackend.organization.web.dto;

import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;

import java.util.List;

public record OrganizationTreeNodeResponse(
        Long organizationId,
        Long parentOrganizationId,
        String code,
        String name,
        String type,
        Long leaderUserId,
        String description,
        Integer sortNo,
        String status,
        List<OrganizationTreeNodeResponse> children
) {

    public OrganizationTreeNodeResponse {
        children = List.copyOf(children);
    }

    public static OrganizationTreeNodeResponse from(
            OrganizationUnit organization,
            List<OrganizationTreeNodeResponse> children
    ) {
        return new OrganizationTreeNodeResponse(
                organization.getOrganizationId(),
                organization.getParentOrganizationId(),
                organization.getOrganizationCode(),
                organization.getOrganizationName(),
                organization.getOrganizationType(),
                organization.getLeaderUserId(),
                organization.getDescription(),
                organization.getSortNo(),
                organization.getStatus(),
                children
        );
    }
}
