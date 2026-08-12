package com.lexpro.lexprobackend.casework.web.dto;

import com.lexpro.lexprobackend.casework.domain.CaseParty;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;

public record CasePartyResponse(
        long partyId,
        long caseId,
        String partyName,
        String partyRole,
        String partyType,
        String identityType,
        String identityNumberMasked,
        String gender,
        LocalDate birthDate,
        Integer age,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static CasePartyResponse from(CaseParty party, LocalDate today) {
        Integer age = party.getBirthDate() == null ? null : Period.between(party.getBirthDate(), today).getYears();
        return new CasePartyResponse(
                party.getPartyId(), party.getCaseId(), party.getPartyName(), party.getPartyRole(), party.getPartyType(),
                party.getIdentityType(), party.getIdentityNumberMasked(), party.getGender(), party.getBirthDate(), age,
                party.getDescription(), party.getCreatedAt(), party.getUpdatedAt()
        );
    }
}
