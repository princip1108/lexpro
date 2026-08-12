package com.lexpro.lexprobackend.processing.web.dto;

import com.lexpro.lexprobackend.processing.domain.DocumentParseResult;

import java.time.OffsetDateTime;

public record DocumentParseSummaryResponse(
        Long docId,
        Long dossierId,
        Long caseId,
        Integer versionNo,
        String parseStatus,
        String parserVersion,
        String errorCode,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        Long requestedBy,
        String requestId,
        Integer durationMs,
        Boolean current
) {
    public static DocumentParseSummaryResponse from(DocumentParseResult result) {
        return new DocumentParseSummaryResponse(result.getDocId(), result.getDossierId(), result.getCaseId(),
                result.getVersionNo(), result.getParseStatus(), result.getParserVersion(), result.getErrorMessage(),
                result.getCreatedAt(), result.getCompletedAt(), result.getRequestedBy(), result.getRequestId(),
                result.getDurationMs(), result.getCurrent());
    }
}
