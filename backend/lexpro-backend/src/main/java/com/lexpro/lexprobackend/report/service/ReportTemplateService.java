package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditEvent;
import com.lexpro.lexprobackend.common.audit.AuditResult;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import com.lexpro.lexprobackend.report.web.dto.CreateReportTemplateRequest;
import com.lexpro.lexprobackend.report.web.dto.ReportTemplateResponse;
import com.lexpro.lexprobackend.report.validation.ReportContentValidationException;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReportTemplateService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED");
    private final ReportTemplateMapper mapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ReportContentValidator contentValidator;

    public ReportTemplateService(ReportTemplateMapper mapper, AuditService auditService, ObjectMapper objectMapper,
                                 ReportContentValidator contentValidator) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.contentValidator = contentValidator;
    }

    @Transactional
    public ReportTemplateResponse create(long userId, CreateReportTemplateRequest request) {
        validateContent(request.content());
        mapper.lockVersions(request.templateCode());
        ReportTemplate previous = mapper.selectLatestByCode(request.templateCode());
        if (previous != null && !previous.getTemplateType().equals(request.templateType())) {
            throw new ApiException(HttpStatus.CONFLICT, "Template type conflict", "TEMPLATE_TYPE_CONFLICT",
                    "All versions of one template code must use the same template type");
        }
        ReportTemplate template = new ReportTemplate();
        template.setTemplateCode(request.templateCode());
        template.setTemplateName(request.templateName().trim());
        template.setTemplateType(request.templateType());
        template.setTemplateContentJson(writeJson(request.content()));
        template.setVersionNo(mapper.selectNextVersion(request.templateCode()));
        template.setSchemaVersion(request.schemaVersion().trim());
        template.setCreatedBy(userId);
        mapper.insert(template);
        auditService.record(new AuditEvent(userId, null, "REPORT_TEMPLATE_CREATED", "REPORT_TEMPLATE",
                String.valueOf(template.getTemplateId()), AuditResult.SUCCESS,
                Map.of("templateCode", request.templateCode(), "versionNo", template.getVersionNo())));
        return ReportTemplateResponse.from(requireTemplate(template.getTemplateId()), objectMapper);
    }

    @Transactional(readOnly = true)
    public List<ReportTemplateResponse> list(String templateType, String status) {
        String checkedStatus = normalizeStatus(status);
        String checkedType = templateType == null || templateType.isBlank() ? null : templateType.trim();
        return mapper.selectList(checkedType, checkedStatus).stream()
                .map(template -> ReportTemplateResponse.from(template, objectMapper)).toList();
    }

    @Transactional(readOnly = true)
    public ReportTemplateResponse detail(long templateId) {
        return ReportTemplateResponse.from(requireTemplate(templateId), objectMapper);
    }

    @Transactional
    public ReportTemplateResponse activate(long userId, long templateId) {
        ReportTemplate template = requireTemplate(templateId);
        mapper.lockVersions(template.getTemplateCode());
        template = requireTemplate(templateId);
        if (!"DRAFT".equals(template.getStatus())) {
            throw statusConflict("Only a draft template can be activated");
        }
        mapper.disableOtherActive(template.getTemplateCode(), templateId);
        if (mapper.activate(templateId) == 0) {
            throw statusConflict("Template is no longer a draft");
        }
        auditService.record(new AuditEvent(userId, null, "REPORT_TEMPLATE_ACTIVATED", "REPORT_TEMPLATE",
                String.valueOf(templateId), AuditResult.SUCCESS,
                Map.of("templateCode", template.getTemplateCode(), "versionNo", template.getVersionNo())));
        return ReportTemplateResponse.from(requireTemplate(templateId), objectMapper);
    }

    @Transactional
    public ReportTemplateResponse disable(long userId, long templateId) {
        ReportTemplate template = requireTemplate(templateId);
        mapper.lockVersions(template.getTemplateCode());
        template = requireTemplate(templateId);
        if (!"ACTIVE".equals(template.getStatus())) {
            throw statusConflict("Only an active template can be disabled");
        }
        if (mapper.disable(templateId) == 0) {
            throw statusConflict("Template is no longer active");
        }
        auditService.record(new AuditEvent(userId, null, "REPORT_TEMPLATE_DISABLED", "REPORT_TEMPLATE",
                String.valueOf(templateId), AuditResult.SUCCESS,
                Map.of("templateCode", template.getTemplateCode(), "versionNo", template.getVersionNo())));
        return ReportTemplateResponse.from(requireTemplate(templateId), objectMapper);
    }

    private ReportTemplate requireTemplate(long templateId) {
        ReportTemplate template = mapper.selectById(templateId);
        if (template == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Report template not found", "REPORT_TEMPLATE_NOT_FOUND",
                    "The requested report template does not exist");
        }
        return template;
    }

    private void validateContent(JsonNode content) {
        try {
            contentValidator.validateTemplate(content);
        } catch (ReportContentValidationException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid template content", "TEMPLATE_CONTENT_INVALID",
                    exception.getMessage());
        }
    }

    private ApiException statusConflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "Template status conflict",
                "REPORT_TEMPLATE_STATUS_CONFLICT", detail);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid template status", "TEMPLATE_STATUS_INVALID",
                    "Template status must be DRAFT, ACTIVE or DISABLED");
        }
        return normalized;
    }

    private String writeJson(JsonNode content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Template content is not JSON serializable", exception);
        }
    }
}
