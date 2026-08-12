package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.StructuredAiClient;
import com.lexpro.lexprobackend.processing.ai.StructuredAiOutput;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OpenAiCompatibleCaseCardGenerationClient implements CaseCardGenerationClient {

    public static final String PROMPT_VERSION = "case-card-v1";
    static final String SYSTEM_PROMPT = """
            Fill a legal case card using only the supplied typed sources. Return one JSON object and no Markdown:
            {"fields":[{"fieldCode":"UPPER_SNAKE_CASE","fieldName":"display name","value":"value or JSON",
            "sourceType":"DOCUMENT|ENTITY|LEGAL_ELEMENT|SUMMARY","sourceId":1,"sourceText":"exact quote",
            "sourceLocation":{},"confidence":0.95}]}.
            Every field must cite one selected source and an exact non-empty quote from that source. Do not infer facts,
            identity numbers or legal conclusions absent from the sources. Use stable, concise field codes and do not
            output duplicate field codes. Confidence must be between 0 and 1.
            """;

    private final StructuredAiClient client;

    public OpenAiCompatibleCaseCardGenerationClient(StructuredAiClient client) {
        this.client = client;
    }

    @Override
    public CaseCardGenerationOutput generate(List<CaseCardSourceMaterial> sources, String requestId) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Case-card sources are required");
        }
        StringBuilder input = new StringBuilder();
        for (CaseCardSourceMaterial source : sources) {
            input.append("\n[").append(source.sourceType()).append(' ')
                    .append(source.sourceId()).append("]\n").append(source.content()).append('\n');
        }
        StructuredAiOutput output = client.generate(SYSTEM_PROMPT, input.toString(), requestId);
        List<CaseCardGeneratedField> fields = parseFields(output.content(), sources);
        ObjectNode parameters = output.generationParameters().deepCopy();
        ArrayNode sourceList = parameters.putArray("caseCardSources");
        sources.forEach(source -> sourceList.addObject()
                .put("sourceType", source.sourceType()).put("sourceId", source.sourceId()));
        return new CaseCardGenerationOutput(fields, output.responseModel(), PROMPT_VERSION, SYSTEM_PROMPT,
                parameters, output.tokenUsage());
    }

    private List<CaseCardGeneratedField> parseFields(JsonNode content, List<CaseCardSourceMaterial> sources) {
        JsonNode fieldArray = content.path("fields");
        if (!fieldArray.isArray() || fieldArray.isEmpty() || fieldArray.size() > 100) {
            throw invalid();
        }
        Map<String, CaseCardSourceMaterial> sourceMap = sources.stream().collect(Collectors.toMap(
                source -> key(source.sourceType(), source.sourceId()), Function.identity()));
        Set<String> codes = new HashSet<>();
        List<CaseCardGeneratedField> fields = new ArrayList<>();
        for (JsonNode node : fieldArray) {
            String code = text(node, "fieldCode");
            String name = text(node, "fieldName");
            String sourceType = text(node, "sourceType");
            long sourceId = node.path("sourceId").canConvertToLong() ? node.path("sourceId").longValue() : -1;
            String quote = text(node, "sourceText");
            JsonNode value = node.get("value");
            BigDecimal confidence = node.path("confidence").isNumber()
                    ? node.path("confidence").decimalValue() : null;
            CaseCardSourceMaterial source = sourceMap.get(key(sourceType, sourceId));
            if (!code.matches("[A-Z][A-Z0-9_]{0,99}") || !codes.add(code)
                    || name.isBlank() || name.length() > 255 || source == null
                    || quote.isBlank() || quote.length() > 10_000 || !source.content().contains(quote)
                    || value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank())
                    || confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0) {
                throw invalid();
            }
            JsonNode location = node.path("sourceLocation");
            if (!location.isMissingNode() && !location.isNull() && !location.isObject()) {
                throw invalid();
            }
            fields.add(new CaseCardGeneratedField(code, name, value.deepCopy(), sourceType, sourceId,
                    quote, location.isObject() ? location.deepCopy() : null, confidence));
        }
        return List.copyOf(fields);
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.textValue().trim() : "";
    }

    private String key(String sourceType, long sourceId) {
        return sourceType + ':' + sourceId;
    }

    private AiClientException invalid() {
        return new AiClientException("AI_RESPONSE_INVALID", "AI case-card output is invalid");
    }
}
