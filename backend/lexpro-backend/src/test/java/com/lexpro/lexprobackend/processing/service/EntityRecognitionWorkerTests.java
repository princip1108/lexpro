package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.EntityRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionSource;
import com.lexpro.lexprobackend.processing.mapper.EntityRecognitionMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRecognitionWorkerTests {

    @Test
    void shouldPersistSuccessfulProviderOutput() throws Exception {
        EntityRecognitionMapper mapper = mock(EntityRecognitionMapper.class);
        EntityRecognitionClient client = mock(EntityRecognitionClient.class);
        EntityRecognitionCompletionService completion = mock(EntityRecognitionCompletionService.class);
        EntityRecognitionRequestedEvent event = event();
        when(mapper.selectSource(9L, 12L)).thenReturn(source());
        EntityRecognitionOutput output = output();
        when(client.recognize("source text", null, "job-1")).thenReturn(output);
        EntityRecognitionWorker worker = new EntityRecognitionWorker(mapper, client,
                new AiProcessingProperties(), completion);

        worker.process(event);

        verify(completion).succeed(eq(event), eq(output), any(Integer.class));
    }

    @Test
    void shouldRecordStableProviderFailureCode() {
        EntityRecognitionMapper mapper = mock(EntityRecognitionMapper.class);
        EntityRecognitionClient client = mock(EntityRecognitionClient.class);
        EntityRecognitionCompletionService completion = mock(EntityRecognitionCompletionService.class);
        when(mapper.selectSource(9L, 12L)).thenReturn(source());
        when(client.recognize("source text", null, "job-1"))
                .thenThrow(new AiClientException("AI_PROVIDER_UNAVAILABLE", "timeout"));
        EntityRecognitionWorker worker = new EntityRecognitionWorker(mapper, client,
                new AiProcessingProperties(), completion);

        worker.process(event());

        verify(completion).fail(eq(event()), eq("AI_PROVIDER_UNAVAILABLE"), any(Integer.class));
    }

    private EntityRecognitionRequestedEvent event() {
        return new EntityRecognitionRequestedEvent(7L, 9L, 12L, "job-1");
    }

    private EntityRecognitionSource source() {
        return new EntityRecognitionSource(12L, 9L, 5L, "SUCCESS", "source text", null);
    }

    private EntityRecognitionOutput output() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return new EntityRecognitionOutput(objectMapper.readTree("{\"entities\":[]}"),
                "deepseek-v4-flash", "prompt-v1", "schema-v1", "prompt",
                objectMapper.readTree("{}"), objectMapper.readTree("{\"total_tokens\":10}"));
    }
}
