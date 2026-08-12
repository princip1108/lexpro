package com.lexpro.lexprobackend.processing.service;

import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import com.lexpro.lexprobackend.processing.mapper.DocumentParseMapper;
import com.lexpro.lexprobackend.processing.parser.DocumentParserRegistry;
import com.lexpro.lexprobackend.processing.parser.DocumentProcessingException;
import com.lexpro.lexprobackend.processing.parser.ParsedDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class DocumentParseWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentParseWorker.class);
    private final DocumentParseMapper mapper;
    private final DocumentParserRegistry parserRegistry;
    private final DocumentParseCompletionService completionService;

    public DocumentParseWorker(DocumentParseMapper mapper, DocumentParserRegistry parserRegistry,
                               DocumentParseCompletionService completionService) {
        this.mapper = mapper;
        this.parserRegistry = parserRegistry;
        this.completionService = completionService;
    }

    public void process(long docId) {
        DocumentParseJob job = mapper.selectJob(docId);
        if (job == null) {
            return;
        }
        Instant started = Instant.now();
        try {
            ParsedDocument parsed = parserRegistry.parse(job);
            completionService.succeed(job, parsed, elapsedMillis(started));
        } catch (DocumentProcessingException exception) {
            log.warn("document_processing_failed docId={} errorCode={}", docId, exception.getErrorCode());
            completionService.fail(job, exception.getErrorCode(), elapsedMillis(started));
        } catch (RuntimeException exception) {
            log.error("document_processing_unexpected_failure docId={}", docId, exception);
            completionService.fail(job, "DOCUMENT_PROCESSING_FAILED", elapsedMillis(started));
        }
    }

    public void reject(long docId) {
        DocumentParseJob job = mapper.selectJob(docId);
        if (job != null) {
            completionService.fail(job, "PROCESSING_QUEUE_FULL", 0);
        }
    }

    private int elapsedMillis(Instant started) {
        long millis = Math.max(0, Duration.between(started, Instant.now()).toMillis());
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }
}
