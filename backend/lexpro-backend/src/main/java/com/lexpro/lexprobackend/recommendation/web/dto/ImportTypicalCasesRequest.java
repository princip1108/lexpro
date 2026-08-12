package com.lexpro.lexprobackend.recommendation.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImportTypicalCasesRequest(
        @NotEmpty @Size(max = 50) List<@Valid TypicalCaseImportItemRequest> cases
) {}
