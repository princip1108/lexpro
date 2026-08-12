package com.lexpro.lexprobackend.casework.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@TableName("case_party")
public class CaseParty {

    @TableId(value = "party_id", type = IdType.AUTO)
    private Long partyId;
    private Long caseId;
    private String partyName;
    private String partyRole;
    private String partyType;
    private String identityType;
    @TableField(select = false)
    private String identityNumber;
    @TableField(select = false)
    private String identityNumberHash;
    private String identityNumberMasked;
    private String gender;
    private LocalDate birthDate;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public Long getCaseId() { return caseId; }
    public void setCaseId(Long caseId) { this.caseId = caseId; }
    public String getPartyName() { return partyName; }
    public void setPartyName(String partyName) { this.partyName = partyName; }
    public String getPartyRole() { return partyRole; }
    public void setPartyRole(String partyRole) { this.partyRole = partyRole; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String partyType) { this.partyType = partyType; }
    public String getIdentityType() { return identityType; }
    public void setIdentityType(String identityType) { this.identityType = identityType; }
    public String getIdentityNumber() { return identityNumber; }
    public void setIdentityNumber(String identityNumber) { this.identityNumber = identityNumber; }
    public String getIdentityNumberHash() { return identityNumberHash; }
    public void setIdentityNumberHash(String identityNumberHash) { this.identityNumberHash = identityNumberHash; }
    public String getIdentityNumberMasked() { return identityNumberMasked; }
    public void setIdentityNumberMasked(String identityNumberMasked) { this.identityNumberMasked = identityNumberMasked; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
