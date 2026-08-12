package com.lexpro.lexprobackend.report.web.dto;

import com.lexpro.lexprobackend.report.domain.CaseReport;

import java.time.OffsetDateTime;

public record CaseReportSummaryResponse(
        Long reportId,
        Long caseId,
        Long templateId,
        Long cardFillTaskId,
        Integer versionNo,
        String reportType,
        String generateMode,
        String reportStatus,
        String reportTitle,
        Boolean current,
        Integer lockVersion,
        String modelName,
        String modelVersion,
        String promptVersion,
        String requestId,
        Integer durationMs,
        OffsetDateTime createdAt,
        OffsetDateTime generatedAt,
        OffsetDateTime updatedAt,
        Long finalizedBy,
        OffsetDateTime finalizedAt,
        String errorCode
) {
    public static CaseReportSummaryResponse from(CaseReport report) {
        return new CaseReportSummaryResponse(report.getReportId(), report.getCaseId(), report.getTemplateId(),
                report.getCardFillTaskId(), report.getVersionNo(), report.getReportType(), report.getGenerateMode(),
                report.getReportStatus(), report.getReportTitle(), report.getCurrent(), report.getLockVersion(),
                report.getModelName(), report.getModelVersion(), report.getPromptVersion(), report.getRequestId(),
                report.getDurationMs(), report.getCreatedAt(), report.getGeneratedAt(), report.getUpdatedAt(),
                report.getFinalizedBy(), report.getFinalizedAt(), report.getErrorMessage());
    }
}
