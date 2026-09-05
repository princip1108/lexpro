package com.lexpro.lexprobackend.recommendation.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record TypicalCaseImportItemRequest(
        @NotBlank @Size(max = 100) String externalCaseId,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String caseCause,
        @Size(max = 50) List<@NotBlank @Size(max = 255) String> caseCauses,
        @Size(max = 50) String caseType,
        @Size(max = 100) String country,
        @Size(max = 255) String court,
        @Size(max = 50) String courtLevel,
        @Size(max = 50) String docType,
        @Size(max = 100) List<@NotBlank @Size(max = 500) String> disputeFocus,
        LocalDate judgmentDate,
        @Size(max = 100) String procedure,
        @Size(max = 100) List<@NotBlank @Size(max = 500) String> applicableLaws,
        @Size(max = 50) String caseLevel,
        @Size(max = 255) String sourceName,
        @Size(max = 255) String sourceFile,
        @Size(max = 2048) String sourceUrl,
        @Size(max = 100) List<@NotBlank @Size(max = 255) String> keywords,
        @Size(max = 500000) String content,
        @Size(max = 200000) String fact,
        @Size(max = 200000) String summary,
        @Size(max = 200000) String prosecutorialProcess,
        @Size(max = 200000) String adjudicationResult,
        @Size(max = 200000) String reasoning,
        @Size(max = 200000) String guidingSignificance
) {
    @AssertTrue(message = "content or fact is required")
    public boolean isContentPresent() {
        return (content != null && !content.isBlank()) || (fact != null && !fact.isBlank());
    }
}
