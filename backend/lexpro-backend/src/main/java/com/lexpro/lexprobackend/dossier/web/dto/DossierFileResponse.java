package com.lexpro.lexprobackend.dossier.web.dto;

import com.lexpro.lexprobackend.dossier.domain.EvidenceFile;

import java.time.OffsetDateTime;
import java.util.List;

public record DossierFileResponse(
        Long dossierId, Long caseId, Long folderId, String fileName, String fileType,
        Long uploadUserId, String uploadUserName, OffsetDateTime uploadedAt, Long fileSize,
        String fileHash, String fileStatus, OffsetDateTime updatedAt, OffsetDateTime deletedAt,
        List<FileTagResponse> tags
) {
    public static DossierFileResponse from(EvidenceFile file, List<FileTagResponse> tags) {
        return new DossierFileResponse(file.getDossierId(), file.getCaseId(), file.getFolderId(),
                file.getFileName(), file.getFileType(), file.getUploadUserId(), file.getUploadUserName(),
                file.getUploadedAt(), file.getFileSize(), file.getFileHash(), file.getFileStatus(),
                file.getUpdatedAt(), file.getDeletedAt(), List.copyOf(tags));
    }
}
