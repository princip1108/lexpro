package com.lexpro.lexprobackend.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateDossierFileRequest(
        @NotBlank @Size(max = 255) String fileName,
        @Positive Long folderId,
        @Size(max = 50) List<@Positive Long> tagIds
) {
}
