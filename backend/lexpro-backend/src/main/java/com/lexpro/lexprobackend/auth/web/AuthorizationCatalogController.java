package com.lexpro.lexprobackend.auth.web;

import com.lexpro.lexprobackend.auth.service.AuthorizationCatalogService;
import com.lexpro.lexprobackend.auth.web.dto.PermissionResponse;
import com.lexpro.lexprobackend.auth.web.dto.RoleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@SecurityRequirement(name = "bearerAuth")
public class AuthorizationCatalogController {

    private final AuthorizationCatalogService catalogService;

    public AuthorizationCatalogController(AuthorizationCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles and their permissions")
    public List<RoleResponse> listRoles() {
        return catalogService.listRoles();
    }

    @GetMapping("/permissions")
    @Operation(summary = "List permissions")
    public List<PermissionResponse> listPermissions() {
        return catalogService.listPermissions();
    }
}
