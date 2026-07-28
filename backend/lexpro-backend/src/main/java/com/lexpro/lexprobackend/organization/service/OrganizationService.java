package com.lexpro.lexprobackend.organization.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lexpro.lexprobackend.organization.domain.OrganizationUnit;
import com.lexpro.lexprobackend.organization.mapper.OrganizationUnitMapper;
import com.lexpro.lexprobackend.organization.web.dto.OrganizationTreeNodeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationService {

    private final OrganizationUnitMapper organizationUnitMapper;

    public OrganizationService(OrganizationUnitMapper organizationUnitMapper) {
        this.organizationUnitMapper = organizationUnitMapper;
    }

    @Transactional(readOnly = true)
    public List<OrganizationTreeNodeResponse> getTree() {
        List<OrganizationUnit> organizations = organizationUnitMapper.selectList(
                Wrappers.<OrganizationUnit>lambdaQuery()
                        .orderByAsc(OrganizationUnit::getSortNo)
                        .orderByAsc(OrganizationUnit::getOrganizationId)
        );
        Map<Long, OrganizationUnit> byId = new LinkedHashMap<>();
        for (OrganizationUnit organization : organizations) {
            if (byId.put(organization.getOrganizationId(), organization) != null) {
                throw new IllegalStateException("Duplicate organization ID found");
            }
        }
        validateHierarchy(byId);

        Map<Long, List<OrganizationUnit>> childrenByParent = new HashMap<>();
        List<OrganizationUnit> roots = new ArrayList<>();
        for (OrganizationUnit organization : organizations) {
            if (organization.getParentOrganizationId() == null) {
                roots.add(organization);
            } else {
                childrenByParent.computeIfAbsent(
                        organization.getParentOrganizationId(),
                        ignored -> new ArrayList<>()
                ).add(organization);
            }
        }
        return roots.stream()
                .map(root -> toTreeNode(root, childrenByParent))
                .toList();
    }

    private void validateHierarchy(Map<Long, OrganizationUnit> byId) {
        Map<Long, VisitState> states = new HashMap<>();
        for (OrganizationUnit organization : byId.values()) {
            validateNode(organization, byId, states);
        }
    }

    private void validateNode(
            OrganizationUnit organization,
            Map<Long, OrganizationUnit> byId,
            Map<Long, VisitState> states
    ) {
        VisitState state = states.get(organization.getOrganizationId());
        if (state == VisitState.VISITED) {
            return;
        }
        if (state == VisitState.VISITING) {
            throw new IllegalStateException("Organization hierarchy contains a cycle");
        }
        states.put(organization.getOrganizationId(), VisitState.VISITING);
        Long parentId = organization.getParentOrganizationId();
        if (parentId != null) {
            OrganizationUnit parent = byId.get(parentId);
            if (parent == null) {
                throw new IllegalStateException("Organization hierarchy contains an orphan node");
            }
            validateNode(parent, byId, states);
        }
        states.put(organization.getOrganizationId(), VisitState.VISITED);
    }

    private OrganizationTreeNodeResponse toTreeNode(
            OrganizationUnit organization,
            Map<Long, List<OrganizationUnit>> childrenByParent
    ) {
        List<OrganizationTreeNodeResponse> children = childrenByParent
                .getOrDefault(organization.getOrganizationId(), List.of())
                .stream()
                .map(child -> toTreeNode(child, childrenByParent))
                .toList();
        return OrganizationTreeNodeResponse.from(organization, children);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
