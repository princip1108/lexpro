package com.lexpro.lexprobackend.report.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;

import java.time.OffsetDateTime;

public record ReportTemplateResponse(
        Long templateId,
        String templateCode,
        String templateName,
        String templateType,
        JsonNode content,
        Integer versionNo,
        String schemaVersion,
        String status,
        Long createdBy,
        OffsetDateTime createdAt
) {
    public static ReportTemplateResponse from(ReportTemplate template, ObjectMapper objectMapper) {
        try {
            return new ReportTemplateResponse(template.getTemplateId(), template.getTemplateCode(),
                    template.getTemplateName(), template.getTemplateType(),
                    objectMapper.readTree(template.getTemplateContentJson()), template.getVersionNo(),
                    template.getSchemaVersion(), template.getStatus(), template.getCreatedBy(),
                    template.getCreatedAt());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored report-template JSON is invalid", exception);
        }
    }
}
