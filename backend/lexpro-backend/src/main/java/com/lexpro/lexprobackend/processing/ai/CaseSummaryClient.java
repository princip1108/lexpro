package com.lexpro.lexprobackend.processing.ai;

import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;

import java.util.List;

public interface CaseSummaryClient {

    CaseSummaryOutput summarize(String summaryType, List<CaseSummarySource> sources, String requestId);
}
