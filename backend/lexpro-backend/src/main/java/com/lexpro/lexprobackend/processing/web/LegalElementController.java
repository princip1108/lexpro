package com.lexpro.lexprobackend.processing.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.processing.service.LegalElementService;
import com.lexpro.lexprobackend.processing.web.dto.ConfirmLegalElementRequest;
import com.lexpro.lexprobackend.processing.web.dto.LegalElementJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.LegalElementResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/documents/{docId}")
@SecurityRequirement(name = "bearerAuth")
public class LegalElementController {

    private final LegalElementService service;

    public LegalElementController(LegalElementService service) {
        this.service = service;
    }

    @PostMapping("/legal-element-jobs")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    @Operation(summary = "Start asynchronous legal-element recognition for one parsed document")
    public ResponseEntity<LegalElementJobResponse> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId,
            HttpServletRequest request) {
        LegalElementJobResponse response = service.start(userId(jwt), caseId, docId,
                RequestIdFilter.getOrCreateRequestId(request));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/cases/" + caseId + "/documents/" + docId
                        + "/legal-element-jobs/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/legal-element-jobs/{requestId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public LegalElementJobResponse job(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                       @PathVariable long docId, @PathVariable String requestId) {
        return service.job(userId(jwt), caseId, docId, requestId);
    }

    @GetMapping("/legal-element-results")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<LegalElementResultResponse> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId) {
        return service.history(userId(jwt), caseId, docId);
    }

    @GetMapping("/legal-element-results/{elementResultId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public LegalElementResultResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId,
            @PathVariable long elementResultId) {
        return service.detail(userId(jwt), caseId, docId, elementResultId);
    }

    @PutMapping("/legal-element-results/{elementResultId}/confirmation")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    public LegalElementResultResponse confirm(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId,
            @PathVariable long elementResultId, @Valid @RequestBody ConfirmLegalElementRequest request) {
        return service.confirm(userId(jwt), caseId, docId, elementResultId, request);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
