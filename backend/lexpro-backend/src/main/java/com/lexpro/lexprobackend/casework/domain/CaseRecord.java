package com.lexpro.lexprobackend.casework.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@TableName("case_record")
public class CaseRecord {

    @TableId(value = "case_id", type = IdType.AUTO)
    private Long caseId;
    private String caseName;
    private String caseNo;
    private String caseType;
    private String caseCause;
    private Long creatorId;
    private LocalDate acceptDate;
    private String caseStatus;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String caseSource;
    private String currentStage;
    private OffsetDateTime deadlineAt;

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
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public LocalDate getAcceptDate() { return acceptDate; }
    public void setAcceptDate(LocalDate acceptDate) { this.acceptDate = acceptDate; }
    public String getCaseStatus() { return caseStatus; }
    public void setCaseStatus(String caseStatus) { this.caseStatus = caseStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCaseSource() { return caseSource; }
    public void setCaseSource(String caseSource) { this.caseSource = caseSource; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public OffsetDateTime getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(OffsetDateTime deadlineAt) { this.deadlineAt = deadlineAt; }
}
