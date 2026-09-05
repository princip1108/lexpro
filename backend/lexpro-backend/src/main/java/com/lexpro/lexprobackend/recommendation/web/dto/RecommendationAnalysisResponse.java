package com.lexpro.lexprobackend.recommendation.web.dto;

import java.time.Instant;
import java.util.List;

public record RecommendationAnalysisResponse(
        String analysisToken,
        Instant expiresAt,
        int sentenceCount,
        int issueCount,
        Timings timings,
        List<Issue> issues
) {
    public record Timings(double qwenMs, double deltaMs, double totalMs) {}

    public record Issue(
            int issueId,
            String text,
            double reliability,
            double weight,
            int matchedSentenceIndex,
            String matchedSentence
    ) {}
}
