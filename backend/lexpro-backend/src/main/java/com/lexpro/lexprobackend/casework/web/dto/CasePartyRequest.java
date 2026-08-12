package com.lexpro.lexprobackend.casework.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CasePartyRequest(
        @NotBlank @Size(max = 255) String partyName,
        @NotBlank @Pattern(regexp = "SUSPECT|VICTIM|WITNESS|OTHER") String partyRole,
        @NotBlank @Pattern(regexp = "PERSON|ORGANIZATION") String partyType,
        @Size(max = 30) String identityType,
        @Size(max = 20) String gender,
        @PastOrPresent LocalDate birthDate,
        String description
) {
}
