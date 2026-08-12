package com.lexpro.lexprobackend.processing.domain;

import java.time.OffsetDateTime;

public class LegalElementJobRecord {

    private String requestId;
    private Long docId;
    private String jobStatus;
    private Long elementResultId;
    private String errorCode;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public String getJobStatus() { return jobStatus; }
    public void setJobStatus(String jobStatus) { this.jobStatus = jobStatus; }
    public Long getElementResultId() { return elementResultId; }
    public void setElementResultId(Long elementResultId) { this.elementResultId = elementResultId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(OffsetDateTime requestedAt) { this.requestedAt = requestedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
