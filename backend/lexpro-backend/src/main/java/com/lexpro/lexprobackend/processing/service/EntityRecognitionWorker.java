package com.lexpro.lexprobackend.processing.service;

import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionSource;
import com.lexpro.lexprobackend.processing.mapper.EntityRecognitionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class EntityRecognitionWorker {

    private static final Logger log = LoggerFactory.getLogger(EntityRecognitionWorker.class);
    private final EntityRecognitionMapper mapper;
    private final EntityRecognitionClient client;
    private final AiProcessingProperties properties;
    private final EntityRecognitionCompletionService completionService;

    public EntityRecognitionWorker(EntityRecognitionMapper mapper, EntityRecognitionClient client,
                                   AiProcessingProperties properties,
                                   EntityRecognitionCompletionService completionService) {
        this.mapper = mapper;
        this.client = client;
        this.properties = properties;
        this.completionService = completionService;
    }

    public void process(EntityRecognitionRequestedEvent event) {
        EntityRecognitionSource source = mapper.selectSource(event.caseId(), event.docId());
        if (source == null || !"SUCCESS".equals(source.parseStatus())
                || source.rawText() == null || source.rawText().isBlank()) {
            completionService.fail(event, "ENTITY_SOURCE_UNAVAILABLE", 0);
            return;
        }
        if (source.rawText().length() > properties.getMaxInputChars()) {
            completionService.fail(event, "AI_INPUT_TOO_LARGE", 0);
            return;
        }
        Instant started = Instant.now();
        try {
            EntityRecognitionOutput output = client.recognize(
                    source.rawText(), source.parsedTextJson(), event.requestId());
            completionService.succeed(event, output, elapsedMillis(started));
        } catch (AiClientException exception) {
            log.warn("entity_recognition_failed docId={} requestId={} errorCode={} category={}", event.docId(),
                    event.requestId(), exception.getErrorCode(), exception.getDiagnosticCode());
            completionService.fail(event, exception.getErrorCode(), elapsedMillis(started));
        } catch (RuntimeException exception) {
            log.error("entity_recognition_unexpected_failure docId={} requestId={}", event.docId(),
                    event.requestId(), exception);
            completionService.fail(event, "ENTITY_RECOGNITION_FAILED", elapsedMillis(started));
        }
    }

    public void reject(EntityRecognitionRequestedEvent event) {
        completionService.reject(event);
    }

    private int elapsedMillis(Instant started) {
        long millis = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
