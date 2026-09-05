package com.lexpro.lexprobackend.recommendation.partner;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PartnerTypicalCaseContract {

    public static final String CONTRACT_VERSION = "partner-v1";

    private PartnerTypicalCaseContract() {}

    public record AnalyzeRequest(@JsonProperty("case_fact") String caseFact) {}

    public record AnalyzeEnvelope(int code, String message, AnalyzeData data) {}

    public record AnalyzeData(
            @JsonProperty("analysis_id") String analysisId,
            @JsonProperty("sentence_count") Integer sentenceCount,
            @JsonProperty("issue_count") Integer issueCount,
            @JsonProperty("qwen_time_ms") Double qwenTimeMs,
            @JsonProperty("delta_time_ms") Double deltaTimeMs,
            @JsonProperty("total_time_ms") Double totalTimeMs,
            List<AnalyzeIssue> issues
    ) {}

    public record AnalyzeIssue(
            @JsonProperty("issue_id") Integer issueId,
            @JsonProperty("issue_text") String issueText,
            Double reliability,
            Double weight,
            @JsonProperty("matched_sentence_index") Integer matchedSentenceIndex,
            @JsonProperty("matched_sentence") String matchedSentence
    ) {}

    public record SearchRequest(
            @JsonProperty("analysis_id") String analysisId,
            Filters filters,
            @JsonProperty("top_k") int topK
    ) {}

    public record Filters(
            String title,
            List<String> casecauses,
            @JsonProperty("applicable_laws") List<String> applicableLaws,
            String caselevel,
            String courtlevel,
            String county,
            LocalDate judgedate,
            String procedure,
            String doctype,
            String court,
            String casetype
    ) {}

    public record SearchEnvelope(int code, String message, SearchData data) {}

    public record SearchData(
            @JsonProperty("retrieval_id") String retrievalId,
            @JsonProperty("analysis_id") String analysisId,
            @JsonProperty("candidate_count") Integer candidateCount,
            @JsonProperty("ranking_rule") String rankingRule,
            @JsonProperty("score_weights") JsonNode scoreWeights,
            List<Candidate> candidates,
            JsonNode timing,
            @JsonProperty("pipeline_timing") JsonNode pipelineTiming
    ) {}

    public record Candidate(
            Long id,
            String title,
            String caseid,
            @JsonProperty("applicable_law") List<String> applicableLaw,
            String casecause,
            List<String> casecausefull,
            String caselevel,
            String casetype,
            String county,
            String court,
            String courtlevel,
            LocalDate judgedate,
            String procedure,
            String doctype,
            Double distance,
            @JsonProperty("fact_similarity") Double factSimilarity,
            @JsonProperty("core_table") String coreTable,
            @JsonProperty("content_table") String contentTable,
            Integer rank,
            @JsonProperty("retrieval_rank") Integer retrievalRank,
            @JsonProperty("issue_raw_score") Double issueRawScore,
            @JsonProperty("issue_score") Double issueScore,
            @JsonProperty("final_score") Double finalScore,
            @JsonProperty("issue_details") List<Map<String, Object>> issueDetails,
            String content
    ) {}
}
