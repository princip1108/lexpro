package com.lexpro.lexprobackend.processing.domain;

public record ParseSourceFile(
        Long dossierId,
        Long caseId,
        String fileName,
        String fileType,
        String fileUrl,
        String fileStatus
) {
}
