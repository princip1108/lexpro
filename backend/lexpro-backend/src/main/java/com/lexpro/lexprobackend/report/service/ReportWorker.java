package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.ai.ReportGenerationClient;
import com.lexpro.lexprobackend.report.ai.ReportGenerationOutput;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.domain.ReportTemplate;
import com.lexpro.lexprobackend.report.mapper.ReportTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class ReportWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);
    private final ReportTemplateMapper templateMapper;
    private final ReportSourceService sourceService;
    private final ReportGenerationClient client;
    private final AiProcessingProperties properties;
    private final ReportCompletionService completionService;
    private final ObjectMapper objectMapper;

    public ReportWorker(ReportTemplateMapper templateMapper, ReportSourceService sourceService,
                        ReportGenerationClient client, AiProcessingProperties properties,
                        ReportCompletionService completionService, ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.sourceService = sourceService;
        this.client = client;
        this.properties = properties;
        this.completionService = completionService;
        this.objectMapper = objectMapper;
    }

    public void process(ReportRequestedEvent event) {
        Instant started = Instant.now();
        try {
            ReportTemplate template = templateMapper.selectById(event.templateId());
            if (template == null || !"ACTIVE".equals(template.getStatus())
                    || !event.reportType().equals(template.getTemplateType())) {
                completionService.fail(event, "REPORT_TEMPLATE_UNAVAILABLE", elapsedMillis(started));
                return;
            }
            List<ReportSourceMaterial> sources = sourceService.load(event.caseId(), event.cardFillTaskId(),
                    event.evidence(), event.legalElementResultIds(), event.typicalCases());
            JsonNode templateContent = readTemplate(template.getTemplateContentJson());
            if (totalChars(sources, template.getTemplateContentJson()) > properties.getMaxInputChars()) {
                completionService.fail(event, "AI_INPUT_TOO_LARGE", elapsedMillis(started));
                return;
            }
            ReportGenerationOutput output = client.generate(event.reportType(), templateContent, sources,
                    event.requestId());
            completionService.succeed(event, output, elapsedMillis(started));
        } catch (ApiException exception) {
            completionService.fail(event, exception.getErrorCode(), elapsedMillis(started));
        } catch (AiClientException exception) {
            log.warn("case_report_failed caseId={} reportId={} errorCode={}", event.caseId(),
                    event.reportId(), exception.getErrorCode());
            completionService.fail(event, exception.getErrorCode(), elapsedMillis(started));
        } catch (RuntimeException exception) {
            log.error("case_report_unexpected_failure caseId={} reportId={}", event.caseId(),
                    event.reportId(), exception);
            completionService.fail(event, "CASE_REPORT_GENERATION_FAILED", elapsedMillis(started));
        }
    }

    public void reject(ReportRequestedEvent event) {
        completionService.reject(event);
    }

    private int totalChars(List<ReportSourceMaterial> sources, String templateJson) {
        long total = templateJson.length();
        for (ReportSourceMaterial source : sources) {
            total += source.content().length() + 128L;
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private JsonNode readTemplate(String templateJson) {
        try {
            return objectMapper.readTree(templateJson);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored report-template JSON is invalid", exception);
        }
    }

    private int elapsedMillis(Instant started) {
        long millis = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
