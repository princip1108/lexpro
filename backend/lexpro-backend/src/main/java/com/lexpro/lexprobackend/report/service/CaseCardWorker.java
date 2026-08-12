package com.lexpro.lexprobackend.report.service;

import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.ai.CaseCardGenerationClient;
import com.lexpro.lexprobackend.report.ai.CaseCardGenerationOutput;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import com.lexpro.lexprobackend.report.mapper.CaseCardMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class CaseCardWorker {

    private static final Logger log = LoggerFactory.getLogger(CaseCardWorker.class);
    private final CaseCardMapper mapper;
    private final CaseCardGenerationClient client;
    private final AiProcessingProperties properties;
    private final CaseCardCompletionService completionService;

    public CaseCardWorker(CaseCardMapper mapper, CaseCardGenerationClient client,
                          AiProcessingProperties properties, CaseCardCompletionService completionService) {
        this.mapper = mapper;
        this.client = client;
        this.properties = properties;
        this.completionService = completionService;
    }

    public void process(CaseCardRequestedEvent event) {
        List<CaseCardSourceMaterial> sources = new ArrayList<>();
        for (var requested : event.sources()) {
            CaseCardSourceMaterial source = mapper.selectSource(event.caseId(), requested.sourceType(),
                    requested.sourceId());
            if (source == null || !Boolean.TRUE.equals(source.ready())) {
                completionService.fail(event, "CASE_CARD_SOURCE_UNAVAILABLE", 0);
                return;
            }
            sources.add(source);
        }
        if (totalChars(sources) > properties.getMaxInputChars()) {
            completionService.fail(event, "AI_INPUT_TOO_LARGE", 0);
            return;
        }
        Instant started = Instant.now();
        try {
            CaseCardGenerationOutput output = client.generate(sources, event.requestId());
            completionService.succeed(event, sources, output, elapsedMillis(started));
        } catch (AiClientException exception) {
            log.warn("case_card_failed caseId={} fillTaskId={} errorCode={}", event.caseId(),
                    event.fillTaskId(), exception.getErrorCode());
            completionService.fail(event, exception.getErrorCode(), elapsedMillis(started));
        } catch (RuntimeException exception) {
            log.error("case_card_unexpected_failure caseId={} fillTaskId={}", event.caseId(),
                    event.fillTaskId(), exception);
            completionService.fail(event, "CASE_CARD_GENERATION_FAILED", elapsedMillis(started));
        }
    }

    public void reject(CaseCardRequestedEvent event) {
        completionService.reject(event);
    }

    private int totalChars(List<CaseCardSourceMaterial> sources) {
        long total = sources.stream().mapToLong(source -> source.content().length() + 64L).sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private int elapsedMillis(Instant started) {
        long millis = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
