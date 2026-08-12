package com.lexpro.lexprobackend.report.domain;

import java.time.OffsetDateTime;

public class CaseReport {

    private Long reportId;
    private Long caseId;
    private Long templateId;
    private Long cardFillTaskId;
    private Integer versionNo;
    private String reportType;
    private String generateMode;
    private Long operatorId;
    private String reportStatus;
    private String reportTitle;
    private String reportContentJson;
    private String errorMessage;
    private OffsetDateTime generatedAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime finalizedAt;
    private Long finalizedBy;
    private Boolean current;
    private OffsetDateTime createdAt;
    private Long createdBy;
    private String contentSchemaVersion;
    private String modelName;
    private String modelVersion;
    private String promptVersion;
    private String promptSnapshot;
    private String generationParametersJson;
    private String tokenUsageJson;
    private String requestId;
    private Integer durationMs;
    private Integer lockVersion;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public Long getCardFillTaskId() { return cardFillTaskId; }
    public void setCardFillTaskId(Long cardFillTaskId) { this.cardFillTaskId = cardFillTaskId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getGenerateMode() { return generateMode; }
    public void setGenerateMode(String generateMode) { this.generateMode = generateMode; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public String getReportTitle() { return reportTitle; }
    public void setReportTitle(String reportTitle) { this.reportTitle = reportTitle; }
    public String getReportContentJson() { return reportContentJson; }
    public void setReportContentJson(String reportContentJson) { this.reportContentJson = reportContentJson; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(OffsetDateTime generatedAt) { this.generatedAt = generatedAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public OffsetDateTime getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(OffsetDateTime finalizedAt) { this.finalizedAt = finalizedAt; }
    public Long getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(Long finalizedBy) { this.finalizedBy = finalizedBy; }
    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getContentSchemaVersion() { return contentSchemaVersion; }
    public void setContentSchemaVersion(String contentSchemaVersion) { this.contentSchemaVersion = contentSchemaVersion; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getPromptSnapshot() { return promptSnapshot; }
    public void setPromptSnapshot(String promptSnapshot) { this.promptSnapshot = promptSnapshot; }
    public String getGenerationParametersJson() { return generationParametersJson; }
    public void setGenerationParametersJson(String generationParametersJson) { this.generationParametersJson = generationParametersJson; }
    public String getTokenUsageJson() { return tokenUsageJson; }
    public void setTokenUsageJson(String tokenUsageJson) { this.tokenUsageJson = tokenUsageJson; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Integer getLockVersion() { return lockVersion; }
    public void setLockVersion(Integer lockVersion) { this.lockVersion = lockVersion; }
}
