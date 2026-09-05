package com.lexpro.lexprobackend.recommendation.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.recommendation.service.RecommendationService;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationAnalysisRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationAnalysisResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/cases/{caseId}/recommendation-analyses")
@SecurityRequirement(name = "bearerAuth")
public class CaseRecommendationAnalysisController {

    private final RecommendationService service;

    public CaseRecommendationAnalysisController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE') and hasAuthority('CASE_READ')")
    public ResponseEntity<RecommendationAnalysisResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody CreateRecommendationAnalysisRequest request, HttpServletRequest httpRequest) {
        RecommendationAnalysisResponse response = service.analyze(
                Long.parseLong(jwt.getSubject()), caseId, request,
                RequestIdFilter.getOrCreateRequestId(httpRequest));
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/recommendation-analyses"))
                .body(response);
    }
}
