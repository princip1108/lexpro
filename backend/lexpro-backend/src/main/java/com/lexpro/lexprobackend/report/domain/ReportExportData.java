package com.lexpro.lexprobackend.report.domain;

public record ReportExportData(
        Long reportId,
        Long caseId,
        Long templateId,
        String reportType,
        String reportStatus,
        String reportTitle,
        String reportContentJson,
        Integer versionNo,
        String caseNo,
        String caseName
) {
}
