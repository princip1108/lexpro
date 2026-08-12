package com.lexpro.lexprobackend.recommendation.domain;

public class RecommendationWrite {

    private Long recommendId;
    private long caseId;
    private Long sourceSummaryId;
    private String queryFactText;
    private String queryEmbedding;
    private String queryDisputeFocusJson;
    private String modelName;
    private String modelVersion;
    private String pipelineVersion;
    private String queryParametersJson;
    private String requestId;
    private int durationMs;
    private long createdBy;

    public Long getRecommendId() { return recommendId; }
    public void setRecommendId(Long recommendId) { this.recommendId = recommendId; }
    public long getCaseId() { return caseId; }
    public void setCaseId(long caseId) { this.caseId = caseId; }
    public Long getSourceSummaryId() { return sourceSummaryId; }
    public void setSourceSummaryId(Long sourceSummaryId) { this.sourceSummaryId = sourceSummaryId; }
    public String getQueryFactText() { return queryFactText; }
    public void setQueryFactText(String queryFactText) { this.queryFactText = queryFactText; }
    public String getQueryEmbedding() { return queryEmbedding; }
    public void setQueryEmbedding(String queryEmbedding) { this.queryEmbedding = queryEmbedding; }
    public String getQueryDisputeFocusJson() { return queryDisputeFocusJson; }
    public void setQueryDisputeFocusJson(String queryDisputeFocusJson) { this.queryDisputeFocusJson = queryDisputeFocusJson; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getPipelineVersion() { return pipelineVersion; }
    public void setPipelineVersion(String pipelineVersion) { this.pipelineVersion = pipelineVersion; }
    public String getQueryParametersJson() { return queryParametersJson; }
    public void setQueryParametersJson(String queryParametersJson) { this.queryParametersJson = queryParametersJson; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public int getDurationMs() { return durationMs; }
    public void setDurationMs(int durationMs) { this.durationMs = durationMs; }
    public long getCreatedBy() { return createdBy; }
    public void setCreatedBy(long createdBy) { this.createdBy = createdBy; }
}
