package com.lexpro.lexprobackend.recommendation.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.recommendation.service.RecommendationService;
import com.lexpro.lexprobackend.recommendation.web.dto.CreateRecommendationRequest;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationDetailResponse;
import com.lexpro.lexprobackend.recommendation.web.dto.RecommendationSummaryResponse;
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
@RequestMapping("/api/v1/cases/{caseId}/recommendations")
@SecurityRequirement(name = "bearerAuth")
public class CaseRecommendationController {

    private final RecommendationService service;

    public CaseRecommendationController(RecommendationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE') and hasAuthority('CASE_READ')")
    public ResponseEntity<RecommendationDetailResponse> create(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody CreateRecommendationRequest request, HttpServletRequest httpRequest) {
        RecommendationDetailResponse response = service.recommend(
                userId(jwt), caseId, request, RequestIdFilter.getOrCreateRequestId(httpRequest));
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId
                + "/recommendations/" + response.recommendId())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE') and hasAuthority('CASE_READ')")
    public List<RecommendationSummaryResponse> history(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable long caseId) {
        return service.history(userId(jwt), caseId);
    }

    @GetMapping("/{recommendId}")
    @PreAuthorize("hasAuthority('RECOMMENDATION_USE') and hasAuthority('CASE_READ')")
    public RecommendationDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                                 @PathVariable long recommendId) {
        return service.detail(userId(jwt), caseId, recommendId);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
