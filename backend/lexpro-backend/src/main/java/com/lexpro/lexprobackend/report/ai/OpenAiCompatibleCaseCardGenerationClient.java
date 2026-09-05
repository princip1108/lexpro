package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.StructuredAiClient;
import com.lexpro.lexprobackend.processing.ai.StructuredAiOutput;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OpenAiCompatibleCaseCardGenerationClient implements CaseCardGenerationClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleCaseCardGenerationClient.class);
    public static final String PROMPT_VERSION = "case-card-v1";
    private static final int MODEL_SOURCE_LIMIT = 6;
    private static final int MODEL_SOURCE_CHAR_BUDGET = 1_800;
    static final String SYSTEM_PROMPT = """
            你是中国检察业务案卡回填助手。只能依据给定的类型化来源填写案卡，返回一个 JSON 对象，不要返回 Markdown：
            {"fields":[{"fieldCode":"UPPER_SNAKE_CASE","fieldName":"display name","value":"value or JSON",
            "sourceType":"DOCUMENT|ENTITY|LEGAL_ELEMENT|SUMMARY","sourceId":1,"sourceText":"exact quote",
            "sourceLocation":{},"confidence":0.95}]}。
            fieldCode 必须使用大写英文字母、数字和下划线；fieldName 以及文本类型的 value 必须使用简体中文。
            每个字段必须引用一个已选来源，并逐字复制该来源中的非空原文作为 sourceText，包括空白和标点，禁止改写。
            只返回最重要且证据充分的 1 至 8 个字段。fieldName 不超过 20 个汉字，文本 value 不超过 100 个汉字，
            sourceText 只引用能够直接支持字段值的最短连续原文且不超过 80 个汉字。
            不得推断来源中没有的事实、身份号码或法律结论。字段编码应稳定、简洁且不得重复。
            sourceId 必须对应已选来源，confidence 必须是 0 到 1 之间的 JSON 数字，不能是字符串。
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
        List<CaseCardSourceMaterial> modelSources = compactSources(sources);
        StringBuilder input = new StringBuilder();
        for (CaseCardSourceMaterial source : modelSources) {
            input.append("\n[").append(source.sourceType()).append(' ')
                    .append(source.sourceId()).append("]\n").append(source.content()).append('\n');
        }
        StructuredAiOutput output = client.generate(SYSTEM_PROMPT, input.toString(), requestId);
        List<CaseCardGeneratedField> fields = parseFields(output.content(), modelSources);
        ObjectNode parameters = output.generationParameters().deepCopy();
        ArrayNode sourceList = parameters.putArray("caseCardSources");
        modelSources.forEach(source -> sourceList.addObject()
                .put("sourceType", source.sourceType()).put("sourceId", source.sourceId()));
        return new CaseCardGenerationOutput(fields, output.responseModel(), PROMPT_VERSION, SYSTEM_PROMPT,
                parameters, output.tokenUsage());
    }

    private List<CaseCardSourceMaterial> compactSources(List<CaseCardSourceMaterial> sources) {
        List<CaseCardSourceMaterial> selected = sources.stream()
                .sorted(Comparator.comparingInt(source -> sourcePriority(source.sourceType())))
                .limit(MODEL_SOURCE_LIMIT)
                .toList();
        int perSourceBudget = Math.max(1, MODEL_SOURCE_CHAR_BUDGET / selected.size());
        return selected.stream().map(source -> new CaseCardSourceMaterial(
                source.sourceType(), source.sourceId(), source.dossierId(),
                prefix(source.content(), perSourceBudget),
                source.ready())).toList();
    }

    private String prefix(String content, int limit) {
        int end = Math.min(content.length(), limit);
        if (end > 0 && end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }
        return content.substring(0, end);
    }

    private int sourcePriority(String sourceType) {
        return switch (sourceType) {
            case "SUMMARY" -> 0;
            case "LEGAL_ELEMENT" -> 1;
            case "ENTITY" -> 2;
            case "DOCUMENT" -> 3;
            default -> 4;
        };
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
            String code = normalizeFieldCode(text(node, "fieldCode"));
            String name = text(node, "fieldName");
            String sourceType = text(node, "sourceType");
            long sourceId = parseLong(node.get("sourceId"));
            String quote = text(node, "sourceText");
            JsonNode value = node.get("value");
            BigDecimal confidence = parseDecimal(node.get("confidence"));
            CaseCardSourceMaterial source = sourceMap.get(key(sourceType, sourceId));
            if (source == null) {
                List<CaseCardSourceMaterial> sameId = sources.stream()
                        .filter(candidate -> candidate.sourceId() == sourceId)
                        .toList();
                if (sameId.size() == 1) {
                    source = sameId.getFirst();
                    sourceType = source.sourceType();
                }
            }
            String diagnostic = null;
            if (!code.matches("[A-Z][A-Z0-9_]{0,99}")) diagnostic = "FIELD_CODE_INVALID";
            else if (!codes.add(code)) diagnostic = "FIELD_CODE_DUPLICATE";
            else if (name.isBlank() || name.length() > 255) diagnostic = "FIELD_NAME_INVALID";
            else if (source == null) diagnostic = "SOURCE_NOT_SELECTED";
            else if (quote.isBlank() || quote.length() > 10_000 || !source.content().contains(quote)) diagnostic = "SOURCE_QUOTE_INVALID";
            else if (value == null || value.isNull() || (value.isTextual() && value.textValue().isBlank())) diagnostic = "FIELD_VALUE_INVALID";
            else if (confidence == null || confidence.compareTo(BigDecimal.ZERO) < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0) diagnostic = "CONFIDENCE_INVALID";
            if (diagnostic != null) {
                log.warn("case_card_ai_field_invalid category={} fieldIndex={} fieldCode={} sourceType={} sourceId={}",
                        diagnostic, fields.size(), code, sourceType, sourceId);
                throw invalid(diagnostic);
            }
            JsonNode location = node.path("sourceLocation");
            if (!location.isMissingNode() && !location.isNull() && !location.isObject()) {
                log.warn("case_card_ai_field_invalid category=SOURCE_LOCATION_INVALID fieldIndex={}", fields.size());
                throw invalid();
            }
            fields.add(new CaseCardGeneratedField(code, name, value.deepCopy(), sourceType, sourceId,
                    quote, location.isObject() ? location.deepCopy() : null, confidence));
        }
        return List.copyOf(fields);
    }

    private String normalizeFieldCode(String value) {
        return value.toUpperCase(Locale.ROOT).replaceAll("[-\\s]+", "_");
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

    private AiClientException invalid(String diagnosticCode) {
        return new AiClientException("AI_RESPONSE_INVALID", diagnosticCode, "AI case-card output is invalid");
    }

    private long parseLong(JsonNode value) {
        if (value == null) return -1;
        if (value.canConvertToLong()) return value.longValue();
        if (value.isTextual()) {
            try { return Long.parseLong(value.textValue().trim()); } catch (NumberFormatException ignored) { }
        }
        return -1;
    }

    private BigDecimal parseDecimal(JsonNode value) {
        if (value == null) return null;
        if (value.isNumber()) return value.decimalValue();
        if (value.isTextual()) {
            try { return new BigDecimal(value.textValue().trim()); } catch (NumberFormatException ignored) { }
        }
        return null;
    }
}
