package com.lexpro.lexprobackend.processing.domain;

import java.time.OffsetDateTime;

public class DocumentParseResult {

    private Long docId;
    private Long dossierId;
    private Long caseId;
    private Integer versionNo;
    private String parseStatus;
    private String parsedTextJson;
    private String rawText;
    private String parserVersion;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private Long requestedBy;
    private String requestId;
    private String parserParametersJson;
    private Integer durationMs;
    private Boolean current;

    public Long getDocId() { return docId; }
    public void setDocId(Long docId) { this.docId = docId; }
    public Long getDossierId() { return dossierId; }
    public void setDossierId(Long dossierId) { this.dossierId = dossierId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public String getParsedTextJson() { return parsedTextJson; }
    public void setParsedTextJson(String parsedTextJson) { this.parsedTextJson = parsedTextJson; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getParserVersion() { return parserVersion; }
    public void setParserVersion(String parserVersion) { this.parserVersion = parserVersion; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public Long getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Long requestedBy) { this.requestedBy = requestedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getParserParametersJson() { return parserParametersJson; }
    public void setParserParametersJson(String parserParametersJson) { this.parserParametersJson = parserParametersJson; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public Boolean getCurrent() { return current; }
    public void setCurrent(Boolean current) { this.current = current; }
}
