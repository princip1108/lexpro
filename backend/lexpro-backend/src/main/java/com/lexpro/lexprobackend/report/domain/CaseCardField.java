package com.lexpro.lexprobackend.report.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class CaseCardField {

    private Long fieldId;
    private Long fillTaskId;
    private String fieldCode;
    private String fieldName;
    private String fieldValue;
    private String fieldValueJson;
    private String sourceText;
    private Long sourceFileId;
    private String sourceLocationJson;
    private BigDecimal confidence;
    private String confirmStatus;
    private Long confirmedBy;
    private OffsetDateTime confirmedAt;

    public Long getFieldId() { return fieldId; }
    public void setFieldId(Long fieldId) { this.fieldId = fieldId; }
    public Long getFillTaskId() { return fillTaskId; }
    public void setFillTaskId(Long fillTaskId) { this.fillTaskId = fillTaskId; }
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getFieldValue() { return fieldValue; }
    public void setFieldValue(String fieldValue) { this.fieldValue = fieldValue; }
    public String getFieldValueJson() { return fieldValueJson; }
    public void setFieldValueJson(String fieldValueJson) { this.fieldValueJson = fieldValueJson; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public Long getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }
    public String getSourceLocationJson() { return sourceLocationJson; }
    public void setSourceLocationJson(String sourceLocationJson) { this.sourceLocationJson = sourceLocationJson; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }
    public Long getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(Long confirmedBy) { this.confirmedBy = confirmedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(OffsetDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
