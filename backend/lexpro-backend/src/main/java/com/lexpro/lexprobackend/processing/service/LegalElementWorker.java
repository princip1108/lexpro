package com.lexpro.lexprobackend.processing.service;

import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.LegalElementSource;
import com.lexpro.lexprobackend.processing.mapper.LegalElementMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class LegalElementWorker {

    private static final Logger log = LoggerFactory.getLogger(LegalElementWorker.class);
    private final LegalElementMapper mapper;
    private final LegalElementRecognitionClient client;
    private final AiProcessingProperties properties;
    private final LegalElementCompletionService completionService;

    public LegalElementWorker(LegalElementMapper mapper, LegalElementRecognitionClient client,
                              AiProcessingProperties properties, LegalElementCompletionService completionService) {
        this.mapper = mapper;
        this.client = client;
        this.properties = properties;
        this.completionService = completionService;
    }

    public void process(LegalElementRequestedEvent event) {
        LegalElementSource source = mapper.selectSource(event.caseId(), event.docId());
        if (source == null || !"SUCCESS".equals(source.parseStatus())
                || source.rawText() == null || source.rawText().isBlank()) {
            completionService.fail(event, "LEGAL_ELEMENT_SOURCE_UNAVAILABLE", 0);
            return;
        }
        if (source.rawText().length() > properties.getMaxInputChars()) {
            completionService.fail(event, "AI_INPUT_TOO_LARGE", 0);
            return;
        }
        Instant started = Instant.now();
        try {
            LegalElementRecognitionOutput output = client.recognize(
                    source.rawText(), source.caseCause(), event.requestId());
            completionService.succeed(event, output, elapsedMillis(started));
        } catch (AiClientException exception) {
            log.warn("legal_element_recognition_failed docId={} requestId={} errorCode={} category={}", event.docId(),
                    event.requestId(), exception.getErrorCode(), exception.getDiagnosticCode());
            completionService.fail(event, exception.getErrorCode(), elapsedMillis(started));
        } catch (RuntimeException exception) {
            log.error("legal_element_recognition_unexpected_failure docId={} requestId={}", event.docId(),
                    event.requestId(), exception);
            completionService.fail(event, "LEGAL_ELEMENT_RECOGNITION_FAILED", elapsedMillis(started));
        }
    }

    public void reject(LegalElementRequestedEvent event) {
        completionService.reject(event);
    }

    private int elapsedMillis(Instant started) {
        long millis = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
