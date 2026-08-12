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

@Component
public class OpenAiCompatibleReportGenerationClient implements ReportGenerationClient {

    public static final String PROMPT_VERSION = "case-report-v1";
    static final String SYSTEM_PROMPT = """
            Draft a legal review report using only the supplied sources. Return one JSON object and no Markdown:
            {"reportContent":{"sections":[{"code":"...","title":"...","content":"..."}]}}.
            Return every template section exactly once and in template order. Preserve each template code and title
            exactly; write only the content. Do not invent facts, evidence, quotations or legal conclusions. Identify
            material conflicts or missing support instead of resolving them by assumption. Do not expose internal
            source IDs or implementation notes in reader-facing text.
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
        StringBuilder input = new StringBuilder("Report type: ").append(reportType)
                .append("\n\nTemplate JSON:\n").append(templateContent).append('\n');
        for (ReportSourceMaterial source : sources) {
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
        sources.forEach(source -> sourceList.addObject()
                .put("sourceType", source.sourceType()).put("sourceId", source.sourceId()));
        return new ReportGenerationOutput(reportContent.deepCopy(), output.responseModel(), PROMPT_VERSION,
                SYSTEM_PROMPT, parameters, output.tokenUsage());
    }
}
