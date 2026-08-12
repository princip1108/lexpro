package com.lexpro.lexprobackend.report.web;

import com.lexpro.lexprobackend.report.service.ReportTemplateService;
import com.lexpro.lexprobackend.report.web.dto.CreateReportTemplateRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTemplateResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/report-templates")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('REPORT_MANAGE')")
public class ReportTemplateController {

    private final ReportTemplateService service;

    public ReportTemplateController(ReportTemplateService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReportTemplateResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody CreateReportTemplateRequest request) {
        ReportTemplateResponse response = service.create(userId(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/report-templates/" + response.templateId())).body(response);
    }

    @GetMapping
    public List<ReportTemplateResponse> list(@RequestParam(required = false) String templateType,
                                             @RequestParam(required = false) String status) {
        return service.list(templateType, status);
    }

    @GetMapping("/{templateId}")
    public ReportTemplateResponse detail(@PathVariable long templateId) {
        return service.detail(templateId);
    }

    @PutMapping("/{templateId}/activation")
    public ReportTemplateResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable long templateId) {
        return service.activate(userId(jwt), templateId);
    }

    @PutMapping("/{templateId}/disablement")
    public ReportTemplateResponse disable(@AuthenticationPrincipal Jwt jwt, @PathVariable long templateId) {
        return service.disable(userId(jwt), templateId);
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
