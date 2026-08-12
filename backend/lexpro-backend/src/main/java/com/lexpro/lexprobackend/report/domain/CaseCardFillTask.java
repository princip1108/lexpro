package com.lexpro.lexprobackend.report.domain;

import java.time.OffsetDateTime;

public class CaseCardFillTask {

    private Long fillTaskId;
    private Long caseId;
    private String fillMode;
    private String fillStatus;
    private Long operatorId;
    private OffsetDateTime createdAt;
    private OffsetDateTime confirmedAt;
    private Long confirmedBy;
    private String modelName;
    private String modelVersion;
    private String promptVersion;
    private String promptSnapshot;
    private String generationParametersJson;
    private String tokenUsageJson;
    private String requestId;
    private Integer durationMs;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;

    public Long getFillTaskId() { return fillTaskId; }
    public void setFillTaskId(Long fillTaskId) { this.fillTaskId = fillTaskId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getFillMode() { return fillMode; }
    public void setFillMode(String fillMode) { this.fillMode = fillMode; }
    public String getFillStatus() { return fillStatus; }
    public void setFillStatus(String fillStatus) { this.fillStatus = fillStatus; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
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
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
