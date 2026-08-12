package com.lexpro.lexprobackend.casework.web;

import com.lexpro.lexprobackend.casework.service.CasePartyService;
import com.lexpro.lexprobackend.casework.web.dto.CasePartyRequest;
import com.lexpro.lexprobackend.casework.web.dto.CasePartyResponse;
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
@RequestMapping("/api/v1/cases/{caseId}/parties")
@SecurityRequirement(name = "bearerAuth")
public class CasePartyController {

    private final CasePartyService casePartyService;

    public CasePartyController(CasePartyService casePartyService) { this.casePartyService = casePartyService; }

    @GetMapping
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<CasePartyResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId) {
        return casePartyService.list(userId(jwt), caseId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CASE_WRITE')")
    public ResponseEntity<CasePartyResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                                    @Valid @RequestBody CasePartyRequest request) {
        CasePartyResponse response = casePartyService.create(userId(jwt), caseId, request);
        return ResponseEntity.created(URI.create("/api/v1/cases/" + caseId + "/parties/" + response.partyId()))
                .body(response);
    }

    @PutMapping("/{partyId}")
    @PreAuthorize("hasAuthority('CASE_WRITE')")
    public CasePartyResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                    @PathVariable long partyId, @Valid @RequestBody CasePartyRequest request) {
        return casePartyService.update(userId(jwt), caseId, partyId, request);
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
