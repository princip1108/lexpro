package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.StructuredAiClient;
import com.lexpro.lexprobackend.processing.ai.StructuredAiOutput;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.validation.ReportContentValidationException;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Comparator;

@Component
public class OpenAiCompatibleReportGenerationClient implements ReportGenerationClient {

    public static final String PROMPT_VERSION = "case-report-v1";
    private static final int MODEL_SOURCE_LIMIT = 6;
    private static final int MODEL_SOURCE_CHAR_BUDGET = 1_800;
    static final String SYSTEM_PROMPT = """
            你是中国检察业务审查报告起草助手。只能依据给定来源起草报告，返回一个 JSON 对象，不要返回 Markdown：
            {"reportContent":{"sections":[{"code":"...","title":"...","content":"..."}]}}.
            所有章节正文必须使用规范、正式的简体中文。模板中的每个章节必须严格按原顺序返回一次，章节 code 和 title
            必须原样保留，只填写 content。不得虚构事实、证据、引文或法律结论；存在重大矛盾或依据不足时应明确说明，
            不得自行假设。面向用户的正文不得暴露内部来源编号或实现说明。
            """;

    private final StructuredAiClient client;
    private final ReportContentValidator contentValidator;

    public OpenAiCompatibleReportGenerationClient(StructuredAiClient client,
                                                   ReportContentValidator contentValidator) {
        this.client = client;
        this.contentValidator = contentValidator;
    }

    @Override
    public ReportGenerationOutput generate(String reportType, JsonNode templateContent,
                                           List<ReportSourceMaterial> sources, String requestId) {
        if (reportType == null || reportType.isBlank() || templateContent == null
                || sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Report type, template and sources are required");
        }
        contentValidator.validateTemplate(templateContent);
        List<ReportSourceMaterial> modelSources = compactSources(sources);
        StringBuilder input = new StringBuilder("Report type: ").append(reportType)
                .append("\n\nTemplate JSON:\n").append(templateContent).append('\n');
        for (ReportSourceMaterial source : modelSources) {
            input.append("\n[").append(source.sourceType()).append(" - ").append(source.label())
                    .append("]\n").append(source.content()).append('\n');
        }
        StructuredAiOutput output = client.generate(SYSTEM_PROMPT, input.toString(), requestId);
        JsonNode reportContent = output.content().path("reportContent");
        try {
            contentValidator.validateReport(reportContent, templateContent);
        } catch (ReportContentValidationException exception) {
            throw new AiClientException("AI_RESPONSE_INVALID", "AI report content is invalid");
        }
        ObjectNode parameters = output.generationParameters().deepCopy();
        parameters.put("reportType", reportType);
        ArrayNode sourceList = parameters.putArray("reportSources");
        modelSources.forEach(source -> sourceList.addObject()
                .put("sourceType", source.sourceType()).put("sourceId", source.sourceId()));
        return new ReportGenerationOutput(reportContent.deepCopy(), output.responseModel(), PROMPT_VERSION,
                SYSTEM_PROMPT, parameters, output.tokenUsage());
    }

    private List<ReportSourceMaterial> compactSources(List<ReportSourceMaterial> sources) {
        List<ReportSourceMaterial> selected = sources.stream()
                .sorted(Comparator.comparingInt(source -> sourcePriority(source.sourceType())))
                .limit(MODEL_SOURCE_LIMIT)
                .toList();
        int perSourceBudget = Math.max(1, MODEL_SOURCE_CHAR_BUDGET / selected.size());
        return selected.stream().map(source -> new ReportSourceMaterial(
                source.sourceType(), source.sourceId(), source.label(),
                prefix(source.content(), perSourceBudget), source.ready())).toList();
    }

    private int sourcePriority(String sourceType) {
        return switch (sourceType) {
            case "CASE_CARD" -> 0;
            case "LEGAL_ELEMENT" -> 1;
            case "EVIDENCE" -> 2;
            case "TYPICAL_CASE" -> 3;
            default -> 4;
        };
    }

    private String prefix(String content, int limit) {
        int end = Math.min(content.length(), limit);
        if (end > 0 && end < content.length() && Character.isHighSurrogate(content.charAt(end - 1))) {
            end--;
        }
        return content.substring(0, end);
    }
}
