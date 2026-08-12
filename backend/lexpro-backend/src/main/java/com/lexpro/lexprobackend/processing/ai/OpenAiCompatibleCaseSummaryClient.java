package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class OpenAiCompatibleCaseSummaryClient implements CaseSummaryClient {

    static final String PROMPT_VERSION = "case-summary-v1";
    static final String SCHEMA_VERSION = "case-summary-v1";
    private static final Set<String> SUMMARY_TYPES = Set.of("FACT", "PROCESS", "CONCLUSION", "FULL");
    static final String SYSTEM_PROMPT = """
            Summarize only the supplied case-document text. Do not infer unsupported facts or legal conclusions.
            Return one JSON object and no Markdown: {"summaryText":"concise summary"}.
            Preserve important names, dates, amounts, procedural events, evidence and conclusions relevant to the
            requested summary type. When documents conflict, state the conflict instead of choosing one version.
            Do not include document IDs or implementation notes in the summary.
            """;

    private final StructuredAiClient client;

    public OpenAiCompatibleCaseSummaryClient(StructuredAiClient client) {
        this.client = client;
    }

    @Override
    public CaseSummaryOutput summarize(String summaryType, List<CaseSummarySource> sources, String requestId) {
        if (!SUMMARY_TYPES.contains(summaryType) || sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Summary type and sources are required");
        }
        StringBuilder input = new StringBuilder("Summary type: ").append(summaryType).append('\n');
        for (CaseSummarySource source : sources) {
            input.append("\n[DOCUMENT ").append(source.docId()).append("]\n").append(source.rawText()).append('\n');
        }
        StructuredAiOutput output = client.generate(SYSTEM_PROMPT, input.toString(), requestId);
        String summaryText = output.content().path("summaryText").isTextual()
                ? output.content().path("summaryText").textValue().trim() : "";
        if (summaryText.isBlank() || summaryText.length() > 50_000) {
            throw new AiClientException("AI_RESPONSE_INVALID", "AI summary text is blank or too large");
        }
        ObjectNode parameters = output.generationParameters().deepCopy();
        parameters.put("summaryType", summaryType);
        ArrayNode sourceIds = parameters.putArray("sourceDocIds");
        sources.forEach(source -> sourceIds.add(source.docId()));
        return new CaseSummaryOutput(summaryText, output.responseModel(), PROMPT_VERSION, SCHEMA_VERSION,
                SYSTEM_PROMPT, parameters, output.tokenUsage());
    }
}
