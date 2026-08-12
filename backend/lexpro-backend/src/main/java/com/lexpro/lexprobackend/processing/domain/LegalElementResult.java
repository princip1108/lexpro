package com.lexpro.lexprobackend.processing.domain;

import java.time.OffsetDateTime;

public class LegalElementResult {

    private Long elementResultId;
    private Long docId;
    private Long caseId;
    private String caseCause;
    private String rawElementsJson;
    private String finalElementsJson;
    private String validationReportJson;
    private String modelName;
    private String modelVersion;
    private String promptVersion;
    private String schemaVersion;
    private String promptSnapshot;
    private String generationParametersJson;
    private String tokenUsageJson;
    private String requestId;
    private Integer durationMs;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private Long confirmedBy;
    private OffsetDateTime confirmedAt;

    public Long getElementResultId() { return elementResultId; }
    public void setElementResultId(Long elementResultId) { this.elementResultId = elementResultId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getCaseCause() { return caseCause; }
    public void setCaseCause(String caseCause) { this.caseCause = caseCause; }
    public String getRawElementsJson() { return rawElementsJson; }
    public void setRawElementsJson(String rawElementsJson) { this.rawElementsJson = rawElementsJson; }
    public String getFinalElementsJson() { return finalElementsJson; }
    public void setFinalElementsJson(String finalElementsJson) { this.finalElementsJson = finalElementsJson; }
    public String getValidationReportJson() { return validationReportJson; }
    public void setValidationReportJson(String validationReportJson) {
        this.validationReportJson = validationReportJson;
    }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getPromptSnapshot() { return promptSnapshot; }
    public void setPromptSnapshot(String promptSnapshot) { this.promptSnapshot = promptSnapshot; }
    public String getGenerationParametersJson() { return generationParametersJson; }
    public void setGenerationParametersJson(String generationParametersJson) {
        this.generationParametersJson = generationParametersJson;
    }
    public String getTokenUsageJson() { return tokenUsageJson; }
    public void setTokenUsageJson(String tokenUsageJson) { this.tokenUsageJson = tokenUsageJson; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
