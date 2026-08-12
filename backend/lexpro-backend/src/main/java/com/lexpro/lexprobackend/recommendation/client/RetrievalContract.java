package com.lexpro.lexprobackend.recommendation.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class RetrievalContract {

    public static final String SCHEMA_VERSION = "1.0";

    private RetrievalContract() {}

    public record ModelInfo(String name, String version, int dimension, String distance) {}

    public record TypicalCaseInput(
            String clientRef, String externalCaseId, String title, String caseCause,
            List<String> caseCauses, String caseType, String country, String court,
            String courtLevel, String docType, List<String> disputeFocus, LocalDate judgmentDate,
            String procedure, List<String> applicableLaws, String caseLevel,
            String content, String fact
    ) {}

    public record NormalizeRequest(String schemaVersion, String requestId, List<TypicalCaseInput> cases) {}

    public record NormalizedTypicalCase(
            String clientRef, String externalCaseId, String title, String caseCause,
            List<String> caseCauses, String caseType, String country, String court,
            String courtLevel, String docType, List<String> disputeFocus, LocalDate judgmentDate,
            String procedure, List<String> applicableLaws, String caseLevel,
            String content, String fact, List<Double> embedding
    ) {}

    public record NormalizeResponse(
            String schemaVersion, String requestId, ModelInfo model, List<NormalizedTypicalCase> cases
    ) {}

    public record Filters(
            String caseCause, String caseType, String courtLevel,
            Integer judgmentYearFrom, Integer judgmentYearTo
    ) {}

    public record RetrieveRequest(
            String schemaVersion, String requestId, String factText,
            List<String> disputeFocus, Filters filters, int limit
    ) {}

    public record RetrievalItem(
            long typicalCaseId, int rank, double score, double lexicalScore,
            Double vectorScore, List<Map<String, Object>> reasons
    ) {}

    public record RetrieveResponse(
            String schemaVersion, String requestId, ModelInfo model, String pipelineVersion,
            Map<String, Object> index, boolean degraded, String degradationReason,
            List<Double> queryEmbedding, List<RetrievalItem> items
    ) {}
}
