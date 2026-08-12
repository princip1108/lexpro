package com.lexpro.lexprobackend.processing.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StartCaseSummaryRequest(
        @Pattern(regexp = "FACT|PROCESS|CONCLUSION|FULL") String summaryType,
        @NotEmpty @Size(max = 20) List<@Positive Long> sourceDocIds
) {
}
