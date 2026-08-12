package com.lexpro.lexprobackend.processing.domain;

public record DocumentParseJob(
        Long docId,
        Long dossierId,
        Long caseId,
        String fileName,
        String fileType,
        String fileUrl,
        String fileStatus,
        String parserParametersJson,
        Long requestedBy,
        String requestId,
        Integer versionNo
) {
}
