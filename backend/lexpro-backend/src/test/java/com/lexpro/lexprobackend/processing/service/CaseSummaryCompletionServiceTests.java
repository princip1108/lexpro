package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.processing.ai.CaseSummaryOutput;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryResult;
import com.lexpro.lexprobackend.processing.mapper.CaseSummaryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseSummaryCompletionServiceTests {

    @Test
    void shouldCreateNextVersionAndMoveCurrentFlagOnlyOnSuccess() {
        CaseSummaryMapper mapper = mock(CaseSummaryMapper.class);
        when(mapper.lockCase(9L)).thenReturn(9L);
        when(mapper.selectNextVersion(9L, "FULL")).thenReturn(3);
        when(mapper.insert(any(CaseSummaryResult.class))).thenAnswer(invocation -> {
            CaseSummaryResult value = invocation.getArgument(0);
            value.setSummaryId(40L);
            return 1;
        });
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setModel("deepseek-v4-flash");
        CaseSummaryCompletionService service = new CaseSummaryCompletionService(mapper, properties,
                mock(AuditService.class), new ObjectMapper());
        CaseSummaryRequestedEvent event = new CaseSummaryRequestedEvent(
                7L, 9L, "FULL", List.of(12L), "job-1");
        CaseSummaryOutput output = new CaseSummaryOutput("summary", "response-model", "prompt-v1", "schema-v1",
                "prompt", new ObjectMapper().createObjectNode(), null);

        service.succeed(event, output, 20);

        ArgumentCaptor<CaseSummaryResult> captor = ArgumentCaptor.forClass(CaseSummaryResult.class);
        verify(mapper).insert(captor.capture());
        assertEquals(3, captor.getValue().getVersionNo());
        InOrder order = inOrder(mapper);
        order.verify(mapper).clearCurrent(9L, "FULL");
        order.verify(mapper).insert(any(CaseSummaryResult.class));
    }
}
