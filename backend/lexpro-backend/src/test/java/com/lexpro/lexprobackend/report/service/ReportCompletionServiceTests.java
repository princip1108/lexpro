package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.report.ai.ReportGenerationOutput;
import com.lexpro.lexprobackend.report.mapper.CaseReportMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportCompletionServiceTests {

    @Test
    void shouldSwitchCurrentOnlyWhenGenerationSucceeds() {
        CaseReportMapper mapper = mock(CaseReportMapper.class);
        when(mapper.lockCase(9L)).thenReturn(9L);
        when(mapper.complete(any())).thenReturn(1);
        ObjectMapper objectMapper = new ObjectMapper();
        ReportCompletionService service = new ReportCompletionService(mapper, mock(AuditService.class), objectMapper);
        ReportRequestedEvent event = new ReportRequestedEvent(7L, 9L, 40L, 3L, "REVIEW_REPORT",
                null, List.of(), List.of(), List.of(), "request-1");
        ReportGenerationOutput output = new ReportGenerationOutput(objectMapper.createObjectNode(),
                "deepseek-v4-flash", "case-report-v1", "prompt", objectMapper.createObjectNode(), null);

        service.succeed(event, output, 120);

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockCase(9L);
        order.verify(mapper).clearCurrent(9L, "REVIEW_REPORT");
        order.verify(mapper).complete(any());
    }
}
