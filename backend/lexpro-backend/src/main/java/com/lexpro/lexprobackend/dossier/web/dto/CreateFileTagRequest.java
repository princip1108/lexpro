package com.lexpro.lexprobackend.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFileTagRequest(
        @NotBlank @Size(max = 100) String tagName,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a six-digit hex color") String color
) {
}
