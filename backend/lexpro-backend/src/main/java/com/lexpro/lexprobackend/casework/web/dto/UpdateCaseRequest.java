package com.lexpro.lexprobackend.casework.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record UpdateCaseRequest(
        @NotBlank @Size(max = 255) String caseName,
        @Size(max = 100) String caseNo,
        @NotBlank @Size(max = 50) String caseType,
        @Size(max = 255) String caseCause,
        @Size(max = 100) String caseSource,
        @Size(max = 50) String currentStage,
        LocalDate acceptDate,
        OffsetDateTime deadlineAt
) {
}
