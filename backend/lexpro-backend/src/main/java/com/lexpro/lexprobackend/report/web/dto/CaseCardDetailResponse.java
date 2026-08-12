package com.lexpro.lexprobackend.report.web.dto;

import java.util.List;

public record CaseCardDetailResponse(
        CaseCardSummaryResponse task,
        List<CaseCardSourceResponse> sources,
        List<CaseCardFieldResponse> fields
) {
}
