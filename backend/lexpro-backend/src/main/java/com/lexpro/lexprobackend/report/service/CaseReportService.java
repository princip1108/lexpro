package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.ai.OpenAiCompatibleReportGenerationClient;
import com.lexpro.lexprobackend.report.domain.CaseReport;
import com.lexpro.lexprobackend.report.domain.ReportEvidenceReference;
import com.lexpro.lexprobackend.report.domain.ReportJobRecord;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.web.dto.CaseReportDetailResponse;
import com.lexpro.lexprobackend.report.web.dto.CaseReportSummaryResponse;
import com.lexpro.lexprobackend.report.web.dto.ReportEvidenceRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportJobResponse;
import com.lexpro.lexprobackend.report.web.dto.ReportTypicalCaseRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTypicalCaseResponse;
import com.lexpro.lexprobackend.report.web.dto.StartReportRequest;
import com.lexpro.lexprobackend.report.web.dto.UpdateReportDraftRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTransitionRequest;
import com.lexpro.lexprobackend.report.web.dto.ReturnReportRequest;
import com.lexpro.lexprobackend.report.validation.ReportContentValidationException;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CaseReportService {

    private final CaseReportMapper mapper;
    private final ReportTemplateMapper templateMapper;
    private final ReportSourceService sourceService;
    private final CaseAccessService caseAccessService;
    private final AiProcessingProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ReportContentValidator contentValidator;

    public CaseReportService(CaseReportMapper mapper, ReportTemplateMapper templateMapper,
                             ReportSourceService sourceService, CaseAccessService caseAccessService,
                             AiProcessingProperties properties, ApplicationEventPublisher eventPublisher,
                             AuditService auditService, ObjectMapper objectMapper,
                             ReportContentValidator contentValidator) {
        this.mapper = mapper;
        this.templateMapper = templateMapper;
        this.sourceService = sourceService;
        this.caseAccessService = caseAccessService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.contentValidator = contentValidator;
    }

    @Transactional
    public ReportJobResponse start(long userId, long caseId, StartReportRequest request, String httpRequestId) {
        caseAccessService.requireEdit(caseId, userId);
        requireProviderAvailable();
        ReportTemplate template = requireActiveTemplate(request.templateId(), request.reportType());
        List<ReportEvidenceRequest> evidence = copy(request.evidence());
        List<Long> elements = copy(request.legalElementResultIds());
        List<ReportTypicalCaseRequest> typicalCases = copy(request.typicalCases());
        requireUniqueReferences(evidence, elements, typicalCases);
        List<ReportSourceMaterial> sources = sourceService.load(caseId, request.cardFillTaskId(), evidence,
                elements, typicalCases);
        if (totalChars(sources, template.getTemplateContentJson()) > properties.getMaxInputChars()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Report input too large", "AI_INPUT_TOO_LARGE",
                    "The selected template and report sources exceed the configured AI input limit");
        }
        if (mapper.lockCase(caseId) == null) {
            throw notFound("Case not found", "CASE_NOT_FOUND");
        }

        String requestId = UUID.randomUUID().toString();
        CaseReport report = new CaseReport();
        report.setCaseId(caseId);
        report.setTemplateId(template.getTemplateId());
        report.setCardFillTaskId(request.cardFillTaskId());
        report.setVersionNo(mapper.selectNextVersion(caseId, request.reportType()));
        report.setReportType(request.reportType());
        report.setOperatorId(userId);
        report.setCreatedBy(userId);
        report.setReportTitle(request.reportTitle().trim());
        report.setContentSchemaVersion(template.getSchemaVersion());
        report.setModelName(properties.getModel());
        report.setPromptVersion(OpenAiCompatibleReportGenerationClient.PROMPT_VERSION);
        report.setRequestId(requestId);
        mapper.insert(report);
        evidence.forEach(reference -> mapper.insertEvidenceReference(report.getReportId(), caseId,
                reference.dossierId(), reference.sortNo()));
        elements.forEach(elementId -> mapper.insertLegalElementReference(report.getReportId(), caseId, elementId));
        typicalCases.forEach(reference -> mapper.insertTypicalCaseReference(report.getReportId(), caseId,
                reference.typicalCaseId(), reference.recommendationItemId(), trim(reference.sectionCode()),
                trim(reference.citationNote()), reference.sortNo(), userId));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reportId", report.getReportId());
        detail.put("reportType", request.reportType());
        detail.put("versionNo", report.getVersionNo());
        detail.put("sourceCount", sources.size());
        if (httpRequestId != null && !httpRequestId.isBlank()) {
            detail.put("httpRequestId", httpRequestId);
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_REPORT_STARTED", "CASE_REPORT",
                String.valueOf(report.getReportId()), AuditResult.SUCCESS, detail), requestId);
        eventPublisher.publishEvent(new ReportRequestedEvent(userId, caseId, report.getReportId(),
                template.getTemplateId(), request.reportType(), request.cardFillTaskId(), evidence, elements,
                typicalCases, requestId));
        return ReportJobResponse.from(mapper.selectJob(caseId, requestId));
    }

    @Transactional(readOnly = true)
    public ReportJobResponse job(long userId, long caseId, String requestId) {
        caseAccessService.requireRead(caseId, userId);
        validateRequestId(requestId);
        ReportJobRecord job = mapper.selectJob(caseId, requestId);
        if (job == null) {
            throw notFound("Report job not found", "CASE_REPORT_JOB_NOT_FOUND");
        }
        return ReportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public List<CaseReportSummaryResponse> history(long userId, long caseId, String reportType) {
        caseAccessService.requireRead(caseId, userId);
        String checkedType = normalizeReportType(reportType);
        return mapper.selectHistory(caseId, checkedType).stream().map(CaseReportSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CaseReportDetailResponse detail(long userId, long caseId, long reportId) {
        caseAccessService.requireRead(caseId, userId);
        return detail(caseId, reportId);
    }

    @Transactional
    public CaseReportDetailResponse updateDraft(long userId, long caseId, long reportId,
                                                UpdateReportDraftRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        CaseReport existing = requireReport(caseId, reportId);
        if (!"DRAFT".equals(existing.getReportStatus())) {
            throw conflict("Only a draft report can be edited", "CASE_REPORT_NOT_DRAFT");
        }
        validateContent(request.content(), requireTemplate(existing.getTemplateId()).getTemplateContentJson());
        CaseReport update = new CaseReport();
        update.setCaseId(caseId);
        update.setReportId(reportId);
        update.setReportTitle(request.reportTitle().trim());
        update.setReportContentJson(writeJson(request.content()));
        update.setLockVersion(request.lockVersion());
        if (mapper.updateDraft(update) == 0) {
            throw conflict("The report was changed by another request", "CASE_REPORT_VERSION_CONFLICT");
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_REPORT_DRAFT_UPDATED", "CASE_REPORT",
                String.valueOf(reportId), AuditResult.SUCCESS,
                Map.of("previousLockVersion", request.lockVersion())));
        return detail(caseId, reportId);
    }

    @Transactional
    public CaseReportDetailResponse submitReview(long userId, long caseId, long reportId,
                                                 ReportTransitionRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        CaseReport report = requireCurrentStatus(caseId, reportId, "DRAFT");
        validateContent(readJson(report.getReportContentJson()),
                requireTemplate(report.getTemplateId()).getTemplateContentJson());
        if (mapper.submitReview(caseId, reportId, request.lockVersion()) == 0) {
            throw transitionConflict();
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_REPORT_REVIEW_SUBMITTED", "CASE_REPORT",
                String.valueOf(reportId), AuditResult.SUCCESS,
                Map.of("previousLockVersion", request.lockVersion())));
        return detail(caseId, reportId);
    }

    @Transactional
    public CaseReportDetailResponse returnToDraft(long userId, long caseId, long reportId,
                                                  ReturnReportRequest request) {
        caseAccessService.requireEdit(caseId, userId);
        requireCurrentStatus(caseId, reportId, "REVIEWING");
        if (mapper.returnToDraft(caseId, reportId, request.lockVersion()) == 0) {
            throw transitionConflict();
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_REPORT_REVIEW_RETURNED", "CASE_REPORT",
                String.valueOf(reportId), AuditResult.SUCCESS,
                Map.of("previousLockVersion", request.lockVersion(), "reason", request.reason().trim())));
        return detail(caseId, reportId);
    }

    @Transactional
    public CaseReportDetailResponse finalizeReport(long userId, long caseId, long reportId,
                                                   ReportTransitionRequest request) {
        caseAccessService.requireManage(caseId, userId);
        requireCurrentStatus(caseId, reportId, "REVIEWING");
        if (mapper.finalizeReport(caseId, reportId, userId, request.lockVersion()) == 0) {
            throw transitionConflict();
        }
        auditService.record(new AuditEvent(userId, caseId, "CASE_REPORT_FINALIZED", "CASE_REPORT",
                String.valueOf(reportId), AuditResult.SUCCESS,
                Map.of("previousLockVersion", request.lockVersion())));
        return detail(caseId, reportId);
    }

    private CaseReportDetailResponse detail(long caseId, long reportId) {
        CaseReport report = requireReport(caseId, reportId);
        JsonNode content = report.getReportContentJson() == null ? null : readJson(report.getReportContentJson());
        List<ReportEvidenceRequest> evidence = mapper.selectEvidenceReferences(caseId, reportId).stream()
                .map(reference -> new ReportEvidenceRequest(reference.dossierId(), reference.sortNo())).toList();
        return new CaseReportDetailResponse(CaseReportSummaryResponse.from(report), content, evidence,
                mapper.selectLegalElementReferences(caseId, reportId),
                mapper.selectTypicalCaseReferences(caseId, reportId).stream()
                        .map(ReportTypicalCaseResponse::from).toList());
    }

    private ReportTemplate requireActiveTemplate(long templateId, String reportType) {
        ReportTemplate template = requireTemplate(templateId);
        if (!"ACTIVE".equals(template.getStatus())) {
            throw conflict("Only an active report template can generate reports", "REPORT_TEMPLATE_NOT_ACTIVE");
        }
        if (!reportType.equals(template.getTemplateType())) {
            throw conflict("Template type does not match report type", "REPORT_TEMPLATE_TYPE_MISMATCH");
        }
        validateTemplate(template.getTemplateContentJson());
        return template;
    }

    private ReportTemplate requireTemplate(Long templateId) {
        if (templateId == null) {
            throw notFound("Report template not found", "REPORT_TEMPLATE_NOT_FOUND");
        }
        ReportTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw notFound("Report template not found", "REPORT_TEMPLATE_NOT_FOUND");
        }
        return template;
    }

    private CaseReport requireReport(long caseId, long reportId) {
        CaseReport report = mapper.selectDetail(caseId, reportId);
        if (report == null) {
            throw notFound("Case report not found", "CASE_REPORT_NOT_FOUND");
        }
        return report;
    }

    private CaseReport requireCurrentStatus(long caseId, long reportId, String status) {
        CaseReport report = requireReport(caseId, reportId);
        if (!status.equals(report.getReportStatus()) || !Boolean.TRUE.equals(report.getCurrent())) {
            throw conflict("Only the current " + status + " report can perform this transition",
                    "CASE_REPORT_TRANSITION_INVALID");
        }
        return report;
    }

    private void requireUniqueReferences(List<ReportEvidenceRequest> evidence, List<Long> elements,
                                         List<ReportTypicalCaseRequest> typicalCases) {
        if (!unique(evidence.stream().map(ReportEvidenceRequest::dossierId).toList())
                || !unique(elements)
                || !unique(typicalCases.stream().map(ReportTypicalCaseRequest::typicalCaseId).toList())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate report reference",
                    "CASE_REPORT_REFERENCES_DUPLICATE", "Each referenced source may appear only once");
        }
    }

    private boolean unique(List<Long> ids) {
        return new HashSet<>(ids).size() == ids.size();
    }

    private int totalChars(List<ReportSourceMaterial> sources, String templateJson) {
        long total = templateJson.length();
        for (ReportSourceMaterial source : sources) {
            total += source.content().length() + 128L;
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private String normalizeReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            return null;
        }
        String normalized = reportType.trim().toUpperCase();
        if (!normalized.matches("[A-Z][A-Z0-9_]{0,49}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report type", "CASE_REPORT_TYPE_INVALID",
                    "Report type must use upper snake case");
        }
        return normalized;
    }

    private void validateContent(JsonNode content, String templateJson) {
        try {
            contentValidator.validateReport(content, readJson(templateJson));
        } catch (ReportContentValidationException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid report content", "CASE_REPORT_CONTENT_INVALID",
                    exception.getMessage());
        }
    }

    private void validateTemplate(String templateJson) {
        try {
            contentValidator.validateTemplate(readJson(templateJson));
        } catch (ReportContentValidationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Invalid report template", "REPORT_TEMPLATE_CONTENT_INVALID",
                    exception.getMessage());
        }
    }

    private ApiException transitionConflict() {
        return conflict("Report status or lock version changed", "CASE_REPORT_TRANSITION_CONFLICT");
    }

    private void requireProviderAvailable() {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI processing disabled", "AI_PROVIDER_DISABLED",
                    "AI processing is not enabled for this environment");
        }
        if (!properties.isAllowExternalCaseData()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "External case-data transfer disabled",
                    "AI_DATA_EXPORT_DISABLED", "External case-data transfer is not enabled for this environment");
        }
    }

    private void validateRequestId(String requestId) {
        try {
            UUID.fromString(requestId);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid request ID", "INVALID_REQUEST_ID",
                    "Request ID must be a UUID");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored report JSON is invalid", exception);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Report content is not JSON serializable", exception);
        }
    }

    private <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private ApiException notFound(String title, String code) {
        return new ApiException(HttpStatus.NOT_FOUND, title, code, title);
    }

    private ApiException conflict(String detail, String code) {
        return new ApiException(HttpStatus.CONFLICT, "Report conflict", code, detail);
    }
}
