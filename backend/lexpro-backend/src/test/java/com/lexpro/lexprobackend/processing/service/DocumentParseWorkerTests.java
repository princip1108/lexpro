package com.lexpro.lexprobackend.processing.service;

import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import com.lexpro.lexprobackend.processing.mapper.DocumentParseMapper;
import com.lexpro.lexprobackend.processing.parser.DocumentParserRegistry;
import com.lexpro.lexprobackend.processing.parser.DocumentProcessingException;
import com.lexpro.lexprobackend.processing.parser.ParsedDocument;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentParseWorkerTests {

    @Test
    void shouldPersistSuccessfulParserOutput() {
        DocumentParseMapper mapper = mock(DocumentParseMapper.class);
        DocumentParserRegistry registry = mock(DocumentParserRegistry.class);
        DocumentParseCompletionService completion = mock(DocumentParseCompletionService.class);
        DocumentParseJob job = job();
        ParsedDocument parsed = new ParsedDocument("text", "{}", "test/1");
        when(mapper.selectJob(12L)).thenReturn(job);
        when(registry.parse(job)).thenReturn(parsed);

        new DocumentParseWorker(mapper, registry, completion).process(12L);

        verify(completion).succeed(org.mockito.ArgumentMatchers.eq(job),
                org.mockito.ArgumentMatchers.eq(parsed), anyInt());
    }

    @Test
    void shouldPersistStableFailureCode() {
        DocumentParseMapper mapper = mock(DocumentParseMapper.class);
        DocumentParserRegistry registry = mock(DocumentParserRegistry.class);
        DocumentParseCompletionService completion = mock(DocumentParseCompletionService.class);
        DocumentParseJob job = job();
        when(mapper.selectJob(12L)).thenReturn(job);
        when(registry.parse(job)).thenThrow(new DocumentProcessingException("PARSER_UNAVAILABLE", "not configured"));

        new DocumentParseWorker(mapper, registry, completion).process(12L);

        verify(completion).fail(org.mockito.ArgumentMatchers.eq(job),
                org.mockito.ArgumentMatchers.eq("PARSER_UNAVAILABLE"), anyInt());
    }

    private DocumentParseJob job() {
        return new DocumentParseJob(12L, 5L, 9L, "source.pdf", "application/pdf",
                "cases/9/private.pdf", "ACTIVE", "{}", 7L, "request-123", 1);
    }
}
