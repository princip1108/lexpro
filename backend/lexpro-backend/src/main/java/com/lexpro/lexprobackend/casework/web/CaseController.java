package com.lexpro.lexprobackend.casework.web;

import com.lexpro.lexprobackend.casework.service.CaseService;
import com.lexpro.lexprobackend.casework.web.dto.CaseDetailResponse;
import com.lexpro.lexprobackend.casework.web.dto.CaseQuery;
import com.lexpro.lexprobackend.casework.web.dto.CaseSummaryResponse;
import com.lexpro.lexprobackend.casework.web.dto.CreateCaseRequest;
import com.lexpro.lexprobackend.casework.web.dto.UpdateCaseRequest;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/cases")
@SecurityRequirement(name = "bearerAuth")
public class CaseController {

    private final CaseService caseService;

    public CaseController(CaseService caseService) { this.caseService = caseService; }

    @GetMapping
    @PreAuthorize("hasAuthority('CASE_READ')")
    @Operation(summary = "List cases visible to the current user")
    public PageResponse<CaseSummaryResponse> listCases(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @ParameterObject @ModelAttribute CaseQuery query) {
        return caseService.listCases(currentUserId(jwt), query);
    }

    @GetMapping("/{caseId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    @Operation(summary = "Get an accessible case")
    public CaseDetailResponse getCase(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        return caseService.getCase(currentUserId(jwt), caseId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CASE_WRITE')")
    @Operation(summary = "Create a case with the current user as its managing assignee")
    public ResponseEntity<CaseDetailResponse> createCase(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody CreateCaseRequest request) {
        CaseDetailResponse response = caseService.createCase(currentUserId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/cases/" + response.caseId())).body(response);
    }

    @PutMapping("/{caseId}")
    @PreAuthorize("hasAuthority('CASE_WRITE')")
    @Operation(summary = "Update case metadata without changing its status")
    public CaseDetailResponse updateCase(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                         @Valid @RequestBody UpdateCaseRequest request) {
        return caseService.updateCase(currentUserId(jwt), caseId, request);
    }

    private long currentUserId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
