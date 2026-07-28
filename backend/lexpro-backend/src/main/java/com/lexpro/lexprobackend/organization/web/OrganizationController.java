package com.lexpro.lexprobackend.organization.web;

import com.lexpro.lexprobackend.organization.service.OrganizationService;
import com.lexpro.lexprobackend.organization.web.dto.OrganizationTreeNodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping("/tree")
    @Operation(summary = "Get the organization hierarchy")
    public List<OrganizationTreeNodeResponse> getTree() {
        return organizationService.getTree();
    }
}
