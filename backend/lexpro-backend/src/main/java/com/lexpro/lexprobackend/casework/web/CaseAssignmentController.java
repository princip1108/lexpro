package com.lexpro.lexprobackend.casework.web;

import com.lexpro.lexprobackend.casework.service.CaseAssignmentService;
import com.lexpro.lexprobackend.casework.web.dto.CaseAssignmentResponse;
import com.lexpro.lexprobackend.casework.web.dto.CreateCaseAssignmentRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/assignments")
@SecurityRequirement(name = "bearerAuth")
public class CaseAssignmentController {

    private final CaseAssignmentService assignmentService;

    public CaseAssignmentController(CaseAssignmentService assignmentService) { this.assignmentService = assignmentService; }

    @GetMapping
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<CaseAssignmentResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        return assignmentService.history(userId(jwt), caseId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CASE_ASSIGN')")
    public ResponseEntity<CaseAssignmentResponse> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                                         @Valid @RequestBody CreateCaseAssignmentRequest request) {
        CaseAssignmentResponse response = assignmentService.assign(userId(jwt), caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/assignments/"
                + response.getAssignmentId())).body(response);
    }

    @PostMapping("/{assignmentId}/end")
    @PreAuthorize("hasAuthority('CASE_ASSIGN')")
    public CaseAssignmentResponse end(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                      @PathVariable long assignmentId) {
        return assignmentService.end(userId(jwt), caseId, assignmentId);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
