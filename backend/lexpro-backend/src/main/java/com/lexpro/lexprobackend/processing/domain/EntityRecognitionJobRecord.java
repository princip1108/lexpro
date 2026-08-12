package com.lexpro.lexprobackend.processing.domain;

import java.time.OffsetDateTime;

public class EntityRecognitionJobRecord {

    private String requestId;
    private Long docId;
    private String jobStatus;
    private Long entityResultId;
    private String errorCode;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getJobStatus() { return jobStatus; }
    public void setJobStatus(String jobStatus) { this.jobStatus = jobStatus; }
    public Long getEntityResultId() { return entityResultId; }
    public void setEntityResultId(Long entityResultId) { this.entityResultId = entityResultId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
