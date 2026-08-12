package com.lexpro.lexprobackend.processing.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.processing.service.DocumentParseService;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseDetailResponse;
import com.lexpro.lexprobackend.processing.web.dto.DocumentParseSummaryResponse;
import com.lexpro.lexprobackend.processing.web.dto.StartDocumentParseRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}")
@SecurityRequirement(name = "bearerAuth")
public class DocumentParseController {

    private final DocumentParseService parseService;

    public DocumentParseController(DocumentParseService parseService) {
        this.parseService = parseService;
    }

    @PostMapping("/dossier/files/{dossierId}/parse-jobs")
    @PreAuthorize("hasAuthority('AI_EXECUTE')")
    @Operation(summary = "Start or retry asynchronous parsing for one dossier file")
    public ResponseEntity<DocumentParseSummaryResponse> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long dossierId,
            @Valid @RequestBody(required = false) StartDocumentParseRequest request,
            HttpServletRequest httpRequest) {
        DocumentParseSummaryResponse response = parseService.start(userId(jwt), caseId, dossierId, request,
                RequestIdFilter.getOrCreateRequestId(httpRequest));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/cases/" + caseId + "/documents/" + response.docId()))
                .body(response);
    }

    @GetMapping("/dossier/files/{dossierId}/parse-results")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<DocumentParseSummaryResponse> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long dossierId) {
        return parseService.history(userId(jwt), caseId, dossierId);
    }

    @GetMapping("/documents/{docId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public DocumentParseDetailResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long docId) {
        return parseService.detail(userId(jwt), caseId, docId);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
