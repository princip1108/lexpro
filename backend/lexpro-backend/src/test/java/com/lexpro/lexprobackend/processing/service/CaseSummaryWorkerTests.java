package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryClient;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import com.lexpro.lexprobackend.processing.mapper.CaseSummaryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseSummaryWorkerTests {

    @Test
    void shouldCompleteSuccessfulSummary() {
        CaseSummaryMapper mapper = mock(CaseSummaryMapper.class);
        CaseSummaryClient client = mock(CaseSummaryClient.class);
        CaseSummaryCompletionService completion = mock(CaseSummaryCompletionService.class);
        List<CaseSummarySource> sources = List.of(new CaseSummarySource(12L, "SUCCESS", "source text"));
        when(mapper.selectSources(9L, List.of(12L))).thenReturn(sources);
        CaseSummaryOutput output = output();
        when(client.summarize("FULL", sources, "job-1")).thenReturn(output);
        CaseSummaryWorker worker = new CaseSummaryWorker(mapper, client,
                new AiProcessingProperties(), completion);

        worker.process(event());

        verify(completion).succeed(eq(event()), eq(output), any(Integer.class));
    }

    @Test
    void shouldFailWhenAnyRequestedSourceIsMissing() {
        CaseSummaryMapper mapper = mock(CaseSummaryMapper.class);
        CaseSummaryCompletionService completion = mock(CaseSummaryCompletionService.class);
        when(mapper.selectSources(9L, List.of(12L))).thenReturn(List.of());
        CaseSummaryWorker worker = new CaseSummaryWorker(mapper, mock(CaseSummaryClient.class),
                new AiProcessingProperties(), completion);

        worker.process(event());

        verify(completion).fail(event(), "SUMMARY_SOURCE_UNAVAILABLE", 0);
    }

    private CaseSummaryRequestedEvent event() {
        return new CaseSummaryRequestedEvent(7L, 9L, "FULL", List.of(12L), "job-1");
    }

    private CaseSummaryOutput output() {
        ObjectMapper mapper = new ObjectMapper();
        return new CaseSummaryOutput("summary", "deepseek-v4-flash", "prompt-v1", "schema-v1",
                "prompt", mapper.createObjectNode(), null);
    }
}
