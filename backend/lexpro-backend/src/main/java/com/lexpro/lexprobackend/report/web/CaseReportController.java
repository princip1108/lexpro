package com.lexpro.lexprobackend.report.web;

import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import com.lexpro.lexprobackend.report.service.CaseReportService;
import com.lexpro.lexprobackend.report.service.ExportedReport;
import com.lexpro.lexprobackend.report.service.ReportExportService;
import com.lexpro.lexprobackend.report.web.dto.CaseReportDetailResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseReportSummaryResponse;
import com.lexpro.lexprobackend.report.web.dto.ReportJobResponse;
import com.lexpro.lexprobackend.report.web.dto.StartReportRequest;
import com.lexpro.lexprobackend.report.web.dto.UpdateReportDraftRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTransitionRequest;
import com.lexpro.lexprobackend.report.web.dto.ReturnReportRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cases/{caseId}")
@SecurityRequirement(name = "bearerAuth")
public class CaseReportController {

    private final CaseReportService service;
    private final ReportExportService exportService;

    public CaseReportController(CaseReportService service, ReportExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @PostMapping("/report-jobs")
    @PreAuthorize("hasAuthority('REPORT_MANAGE') and hasAuthority('AI_EXECUTE')")
    public ResponseEntity<ReportJobResponse> start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
            @Valid @RequestBody StartReportRequest request, HttpServletRequest httpRequest) {
        ReportJobResponse response = service.start(userId(jwt), caseId, request,
                RequestIdFilter.getOrCreateRequestId(httpRequest));
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/cases/" + caseId + "/report-jobs/" + response.requestId()))
                .body(response);
    }

    @GetMapping("/report-jobs/{requestId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public ReportJobResponse job(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                 @PathVariable String requestId) {
        return service.job(userId(jwt), caseId, requestId);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public List<CaseReportSummaryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                                   @RequestParam(required = false) String reportType) {
        return service.history(userId(jwt), caseId, reportType);
    }

    @GetMapping("/reports/{reportId}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public CaseReportDetailResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                           @PathVariable long reportId) {
        return service.detail(userId(jwt), caseId, reportId);
    }

    @PutMapping("/reports/{reportId}/draft")
    @PreAuthorize("hasAuthority('REPORT_MANAGE')")
    public CaseReportDetailResponse updateDraft(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long reportId,
            @Valid @RequestBody UpdateReportDraftRequest request) {
        return service.updateDraft(userId(jwt), caseId, reportId, request);
    }

    @PutMapping("/reports/{reportId}/review-submission")
    @PreAuthorize("hasAuthority('REPORT_MANAGE')")
    public CaseReportDetailResponse submitReview(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long reportId,
            @Valid @RequestBody ReportTransitionRequest request) {
        return service.submitReview(userId(jwt), caseId, reportId, request);
    }

    @PutMapping("/reports/{reportId}/review-return")
    @PreAuthorize("hasAuthority('REPORT_MANAGE')")
    public CaseReportDetailResponse returnToDraft(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long reportId,
            @Valid @RequestBody ReturnReportRequest request) {
        return service.returnToDraft(userId(jwt), caseId, reportId, request);
    }

    @PutMapping("/reports/{reportId}/finalization")
    @PreAuthorize("hasAuthority('REPORT_MANAGE')")
    public CaseReportDetailResponse finalizeReport(
            @AuthenticationPrincipal Jwt jwt, @PathVariable long caseId, @PathVariable long reportId,
            @Valid @RequestBody ReportTransitionRequest request) {
        return service.finalizeReport(userId(jwt), caseId, reportId, request);
    }

    @GetMapping("/reports/{reportId}/exports/{format}")
    @PreAuthorize("hasAuthority('CASE_READ')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable long caseId,
                                         @PathVariable long reportId, @PathVariable String format) {
        ExportedReport export = exportService.export(userId(jwt), caseId, reportId, format);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(export.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(export.content());
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
