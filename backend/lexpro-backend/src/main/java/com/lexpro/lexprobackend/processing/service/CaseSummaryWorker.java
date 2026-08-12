package com.lexpro.lexprobackend.processing.service;

import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryClient;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import com.lexpro.lexprobackend.processing.mapper.CaseSummaryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class CaseSummaryWorker {

    private static final Logger log = LoggerFactory.getLogger(CaseSummaryWorker.class);
    private final CaseSummaryMapper mapper;
    private final CaseSummaryClient client;
    private final AiProcessingProperties properties;
    private final CaseSummaryCompletionService completionService;

    public CaseSummaryWorker(CaseSummaryMapper mapper, CaseSummaryClient client,
                             AiProcessingProperties properties, CaseSummaryCompletionService completionService) {
        this.mapper = mapper;
        this.client = client;
        this.properties = properties;
        this.completionService = completionService;
    }

    public void process(CaseSummaryRequestedEvent event) {
        List<CaseSummarySource> sources = mapper.selectSources(event.caseId(), event.sourceDocIds());
        if (!validSources(sources, event.sourceDocIds().size())) {
            completionService.fail(event, "SUMMARY_SOURCE_UNAVAILABLE", 0);
            return;
        }
        if (totalChars(sources) > properties.getMaxInputChars()) {
            completionService.fail(event, "AI_INPUT_TOO_LARGE", 0);
            return;
        }
        Instant started = Instant.now();
        try {
            CaseSummaryOutput output = client.summarize(event.summaryType(), sources, event.requestId());
            completionService.succeed(event, output, elapsedMillis(started));
        } catch (AiClientException exception) {
            log.warn("case_summary_failed caseId={} requestId={} errorCode={}", event.caseId(),
                    event.requestId(), exception.getErrorCode());
            completionService.fail(event, exception.getErrorCode(), elapsedMillis(started));
        } catch (RuntimeException exception) {
            log.error("case_summary_unexpected_failure caseId={} requestId={}", event.caseId(),
                    event.requestId(), exception);
            completionService.fail(event, "CASE_SUMMARY_FAILED", elapsedMillis(started));
        }
    }

    public void reject(CaseSummaryRequestedEvent event) {
        completionService.reject(event);
    }

    private boolean validSources(List<CaseSummarySource> sources, int expectedCount) {
        return sources != null && sources.size() == expectedCount && sources.stream()
                .allMatch(source -> "SUCCESS".equals(source.parseStatus())
                        && source.rawText() != null && !source.rawText().isBlank());
    }

    private int totalChars(List<CaseSummarySource> sources) {
        long total = sources.stream().mapToLong(source -> source.rawText().length() + 64L).sum();
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private int elapsedMillis(Instant started) {
        long millis = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
