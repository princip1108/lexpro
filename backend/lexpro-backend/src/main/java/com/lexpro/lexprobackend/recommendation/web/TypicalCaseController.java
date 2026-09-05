package com.lexpro.lexprobackend.recommendation.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.common.web.dto.PageResponse;
import com.lexpro.lexprobackend.recommendation.service.RecommendationService;
import com.lexpro.lexprobackend.recommendation.web.dto.ImportTypicalCasesRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.TypicalCaseResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/typical-cases")
@SecurityRequirement(name = "bearerAuth")
public class TypicalCaseController {

    private final RecommendationService service;

    public TypicalCaseController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping("/imports")
    @PreAuthorize("hasAuthority('REPORT_MANAGE') and hasAuthority('AI_EXECUTE')")
    public ResponseEntity<List<TypicalCaseResponse>> importCases(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ImportTypicalCasesRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(201).body(service.importTypicalCases(
                userId(jwt), request, RequestIdFilter.getOrCreateRequestId(httpRequest)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE')")
    public PageResponse<TypicalCaseResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String caseCause,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) String court,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String sourceName,
            @RequestParam(required = false) String caseLevel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate judgmentDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate judgmentDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate judgmentDateTo,
            @RequestParam(defaultValue = "false") boolean favoritesOnly,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.typicalCases(userId(jwt), keyword, caseCause, caseType, court, region, docType,
                sourceName, caseLevel, judgmentDate, judgmentDateFrom, judgmentDateTo,
                favoritesOnly, page, size);
    }

    @GetMapping("/{typicalCaseId}")
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE')")
    public TypicalCaseResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long typicalCaseId) {
        return service.typicalCase(userId(jwt), typicalCaseId);
    }

    @PutMapping("/{typicalCaseId}/favorite")
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE')")
    public ResponseEntity<Void> favorite(@AuthenticationPrincipal Jwt jwt, @PathVariable long typicalCaseId) {
        service.favorite(userId(jwt), typicalCaseId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{typicalCaseId}/favorite")
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE')")
    public ResponseEntity<Void> unfavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable long typicalCaseId) {
        service.unfavorite(userId(jwt), typicalCaseId);
        return ResponseEntity.noContent().build();
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
