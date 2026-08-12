package com.lexpro.lexprobackend.casework.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CreateCaseAssignmentRequest(
        @NotNull @Positive Long userId,
        @NotNull @Pattern(regexp = "PROSECUTOR|ASSIGNEE|REVIEWER|COLLABORATOR") String assignmentRole,
        @NotNull @Pattern(regexp = "VIEW|EDIT|MANAGE") String accessLevel
) {
}
