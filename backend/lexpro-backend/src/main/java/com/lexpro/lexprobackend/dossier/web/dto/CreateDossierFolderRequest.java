package com.lexpro.lexprobackend.dossier.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateDossierFolderRequest(
        @NotBlank @Size(max = 255) String folderName,
        @Positive Long parentFolderId,
        @PositiveOrZero Integer sortNo
) {
}
