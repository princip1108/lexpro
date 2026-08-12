package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionClient;
import com.lexpro.lexprobackend.processing.ai.LegalElementRecognitionOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.LegalElementSource;
import com.lexpro.lexprobackend.processing.mapper.LegalElementMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalElementWorkerTests {

    @Test
    void shouldCompleteSuccessfulRecognition() throws Exception {
        LegalElementMapper mapper = mock(LegalElementMapper.class);
        LegalElementRecognitionClient client = mock(LegalElementRecognitionClient.class);
        LegalElementCompletionService completion = mock(LegalElementCompletionService.class);
        when(mapper.selectSource(9L, 12L)).thenReturn(source());
        LegalElementRecognitionOutput output = output();
        when(client.recognize("source text", "Fraud", "job-1")).thenReturn(output);
        LegalElementWorker worker = new LegalElementWorker(mapper, client,
                new AiProcessingProperties(), completion);

        worker.process(event());

        verify(completion).succeed(eq(event()), eq(output), any(Integer.class));
    }

    @Test
    void shouldRecordProviderFailure() {
        LegalElementMapper mapper = mock(LegalElementMapper.class);
        LegalElementRecognitionClient client = mock(LegalElementRecognitionClient.class);
        LegalElementCompletionService completion = mock(LegalElementCompletionService.class);
        when(mapper.selectSource(9L, 12L)).thenReturn(source());
        when(client.recognize("source text", "Fraud", "job-1"))
                .thenThrow(new AiClientException("AI_PROVIDER_UNAVAILABLE", "timeout"));
        LegalElementWorker worker = new LegalElementWorker(mapper, client,
                new AiProcessingProperties(), completion);

        worker.process(event());

        verify(completion).fail(eq(event()), eq("AI_PROVIDER_UNAVAILABLE"), any(Integer.class));
    }

    private LegalElementRequestedEvent event() {
        return new LegalElementRequestedEvent(7L, 9L, 12L, "job-1");
    }

    private LegalElementSource source() {
        return new LegalElementSource(12L, 9L, 5L, "SUCCESS", "source text", "Fraud");
    }

    private LegalElementRecognitionOutput output() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.readTree("{\"elements\":[]}");
        return new LegalElementRecognitionOutput(json, mapper.createObjectNode(), "Fraud",
                "deepseek-v4-flash", "prompt-v1", "schema-v1", "prompt",
                mapper.createObjectNode(), null);
    }
}
