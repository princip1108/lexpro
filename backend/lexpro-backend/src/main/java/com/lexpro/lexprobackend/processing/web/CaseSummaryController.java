package com.lexpro.lexprobackend.processing.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.processing.service.CaseSummaryService;
import com.lexpro.lexprobackend.processing.web.dto.CaseSummaryJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.CaseSummaryResponse;
import com.lexpro.lexprobackend.processing.web.dto.StartCaseSummaryRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}")
@SecurityRequirement(name = "bearerAuth")
public class CaseSummaryController {

    private final CaseSummaryService service;

    public CaseSummaryController(CaseSummaryService service) {
        this.service = service;
    }

    @PostMapping("/summary-jobs")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    @Operation(summary = "Start asynchronous case-summary generation from selected parsed documents")
    public ResponseEntity<CaseSummaryJobResponse> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody StartCaseSummaryRequest request, HttpServletRequest httpRequest) {
        CaseSummaryJobResponse response = service.start(userId(jwt), caseId, request,
                RequestIdFilter.getOrCreateRequestId(httpRequest));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/cases/" + caseId + "/summary-jobs/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/summary-jobs/{requestId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public CaseSummaryJobResponse job(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                      @PathVariable String requestId) {
        return service.job(userId(jwt), caseId, requestId);
    }

    @GetMapping("/summaries")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<CaseSummaryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                             @RequestParam String summaryType) {
        return service.history(userId(jwt), caseId, summaryType);
    }

    @GetMapping("/summaries/{summaryId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public CaseSummaryResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                      @PathVariable long summaryId) {
        return service.detail(userId(jwt), caseId, summaryId);
    }

    @PutMapping("/summaries/{summaryId}/confirmation")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    public CaseSummaryResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                       @PathVariable long summaryId) {
        return service.confirm(userId(jwt), caseId, summaryId);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
