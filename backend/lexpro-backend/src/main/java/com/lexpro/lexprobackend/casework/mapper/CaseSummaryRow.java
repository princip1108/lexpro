package com.lexpro.lexprobackend.casework.mapper;

import com.lexpro.lexprobackend.casework.web.dto.CaseSummaryResponse;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public class CaseSummaryRow {
    private Long caseId;
    private String caseName;
    private String caseNo;
    private String caseType;
    private String caseCause;
    private String caseSource;
    private String currentStage;
    private String caseStatus;
    private LocalDate acceptDate;
    private OffsetDateTime deadlineAt;
    private Boolean overdue;
    private String suspectName;
    private Long dossierCount;
    private String handlerName;
    private OffsetDateTime updatedAt;

    public CaseSummaryResponse toResponse() {
        return new CaseSummaryResponse(caseId, caseName, caseNo, caseType, caseCause, caseSource,
                currentStage, caseStatus, acceptDate, deadlineAt, Boolean.TRUE.equals(overdue),
                suspectName, dossierCount == null ? 0 : dossierCount, handlerName, updatedAt);
    }

    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getCaseName() { return caseName; }
    public void setCaseName(String caseName) { this.caseName = caseName; }
    public String getCaseNo() { return caseNo; }
    public void setCaseNo(String caseNo) { this.caseNo = caseNo; }
    public String getCaseType() { return caseType; }
    public void setCaseType(String caseType) { this.caseType = caseType; }
    public String getCaseCause() { return caseCause; }
    public void setCaseCause(String caseCause) { this.caseCause = caseCause; }
    public String getCaseSource() { return caseSource; }
    public void setCaseSource(String caseSource) { this.caseSource = caseSource; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String caseStatus) { this.caseStatus = caseStatus; }
    public LocalDate getAcceptDate() { return acceptDate; }
    public void setAcceptDate(LocalDate acceptDate) { this.acceptDate = acceptDate; }
    public OffsetDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(OffsetDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
    public Boolean getOverdue() { return overdue; }
    public void setOverdue(Boolean overdue) { this.overdue = overdue; }
    public String getSuspectName() { return suspectName; }
    public void setSuspectName(String suspectName) { this.suspectName = suspectName; }
    public Long getDossierCount() { return dossierCount; }
    public void setDossierCount(Long dossierCount) { this.dossierCount = dossierCount; }
    public String getHandlerName() { return handlerName; }
    public void setHandlerName(String handlerName) { this.handlerName = handlerName; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
