package com.lexpro.lexprobackend.processing.domain;

public record CaseSummarySource(
        Long docId,
        String parseStatus,
        String rawText
) {
}
