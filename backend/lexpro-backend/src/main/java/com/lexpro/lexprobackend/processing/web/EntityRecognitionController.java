package com.lexpro.lexprobackend.processing.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.processing.service.EntityRecognitionService;
import com.lexpro.lexprobackend.processing.web.dto.ConfirmEntityRecognitionRequest;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionJobResponse;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionResultResponse;
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
public class EntityRecognitionController {

    private final EntityRecognitionService service;

    public EntityRecognitionController(EntityRecognitionService service) {
        this.service = service;
    }

    @PostMapping("/entity-recognition-jobs")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    @Operation(summary = "Start asynchronous entity recognition for one parsed document")
    public ResponseEntity<EntityRecognitionJobResponse> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId,
            HttpServletRequest request) {
        EntityRecognitionJobResponse response = service.start(userId(jwt), caseId, docId,
                RequestIdFilter.getOrCreateRequestId(request));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/cases/" + caseId + "/documents/" + docId
                        + "/entity-recognition-jobs/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/entity-recognition-jobs/{requestId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public EntityRecognitionJobResponse job(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                            @PathVariable long docId, @PathVariable String requestId) {
        return service.job(userId(jwt), caseId, docId, requestId);
    }

    @GetMapping("/entity-results")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<EntityRecognitionResultResponse> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId) {
        return service.history(userId(jwt), caseId, docId);
    }

    @GetMapping("/entity-results/{entityResultId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public EntityRecognitionResultResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId,
            @PathVariable long entityResultId) {
        return service.detail(userId(jwt), caseId, docId, entityResultId);
    }

    @PutMapping("/entity-results/{entityResultId}/confirmation")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    public EntityRecognitionResultResponse confirm(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId,
            @PathVariable long entityResultId, @Valid @RequestBody ConfirmEntityRecognitionRequest request) {
        return service.confirm(userId(jwt), caseId, docId, entityResultId, request);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
