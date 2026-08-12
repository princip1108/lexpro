package com.lexpro.lexprobackend.dossier.web.dto;

import com.lexpro.lexprobackend.dossier.domain.DossierFolder;

import java.time.OffsetDateTime;
import java.util.List;

public record DossierFolderResponse(
        Long folderId, Long caseId, Long parentFolderId, String folderName, Integer sortNo,
        OffsetDateTime createdAt, List<DossierFolderResponse> children
) {
    public static DossierFolderResponse from(DossierFolder folder, List<DossierFolderResponse> children) {
        return new DossierFolderResponse(folder.getFolderId(), folder.getCaseId(), folder.getParentFolderId(),
                folder.getFolderName(), folder.getSortNo(), folder.getCreatedAt(), children);
    }
}
