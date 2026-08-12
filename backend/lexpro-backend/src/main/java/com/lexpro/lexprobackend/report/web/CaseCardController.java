package com.lexpro.lexprobackend.report.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.report.service.CaseCardService;
import com.lexpro.lexprobackend.report.web.dto.CaseCardDetailResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardFieldResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardJobResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseCardSummaryResponse;
import com.lexpro.lexprobackend.report.web.dto.ConfirmCaseCardFieldRequest;
import com.lexpro.lexprobackend.report.web.dto.StartCaseCardRequest;
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
@RequestMapping("/api/v1/cases/{caseId}")
@SecurityRequirement(name = "bearerAuth")
public class CaseCardController {

    private final CaseCardService service;

    public CaseCardController(CaseCardService service) {
        this.service = service;
    }

    @PostMapping("/case-card-jobs")
    @PreAuthorize("hasAuthority('REPORT_MANAGE') and hasAuthority('AI_EXECUTE')")
    @Operation(summary = "Start asynchronous case-card generation from typed sources")
    public ResponseEntity<CaseCardJobResponse> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody StartCaseCardRequest request, HttpServletRequest httpRequest) {
        CaseCardJobResponse response = service.start(userId(jwt), caseId, request,
                RequestIdFilter.getOrCreateRequestId(httpRequest));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/cases/" + caseId + "/case-card-jobs/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/case-card-jobs/{requestId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public CaseCardJobResponse job(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                   @PathVariable String requestId) {
        return service.job(userId(jwt), caseId, requestId);
    }

    @GetMapping("/case-cards")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<CaseCardSummaryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        return service.history(userId(jwt), caseId);
    }

    @GetMapping("/case-cards/{fillTaskId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public CaseCardDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                         @PathVariable long fillTaskId) {
        return service.detail(userId(jwt), caseId, fillTaskId);
    }

    @PutMapping("/case-cards/{fillTaskId}/fields/{fieldId}/confirmation")
    @PreAuthorize("hasAuthority('REPORT_MANAGE')")
    public CaseCardFieldResponse confirmField(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long fillTaskId,
            @PathVariable long fieldId, @Valid @RequestBody ConfirmCaseCardFieldRequest request) {
        return service.confirmField(userId(jwt), caseId, fillTaskId, fieldId, request);
    }

    @PutMapping("/case-cards/{fillTaskId}/confirmation")
    @PreAuthorize("hasAuthority('REPORT_MANAGE')")
    public CaseCardDetailResponse confirmTask(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                              @PathVariable long fillTaskId) {
        return service.confirmTask(userId(jwt), caseId, fillTaskId);
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
