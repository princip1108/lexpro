package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.CaseSummaryResult;
import com.lexpro.lexprobackend.processing.domain.CaseSummarySource;
import com.lexpro.lexprobackend.processing.mapper.CaseSummaryMapper;
import com.lexpro.lexprobackend.processing.web.dto.StartCaseSummaryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseSummaryServiceTests {

    @Test
    void shouldStartSummaryFromExplicitSources() {
        CaseSummaryMapper mapper = mock(CaseSummaryMapper.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.lockCase(9L)).thenReturn(9L);
        when(mapper.selectSources(9L, List.of(12L))).thenReturn(List.of(source()));
        CaseSummaryService service = service(mapper, accessService, publisher);

        var response = service.start(7L, 9L,
                new StartCaseSummaryRequest("FULL", List.of(12L)), "http-1");

        assertEquals("PROCESSING", response.status());
        assertEquals("FULL", response.summaryType());
        assertNotNull(response.requestId());
        verify(accessService).requireEdit(9L, 7L);
        verify(publisher).publishEvent(any(CaseSummaryRequestedEvent.class));
    }

    @Test
    void shouldRejectDuplicateSourceIds() {
        CaseSummaryService service = service(mock(CaseSummaryMapper.class), mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));

        ApiException exception = assertThrows(ApiException.class, () -> service.start(7L, 9L,
                new StartCaseSummaryRequest("FULL", List.of(12L, 12L)), "http-1"));

        assertEquals("SUMMARY_SOURCES_DUPLICATE", exception.getErrorCode());
    }

    @Test
    void shouldConfirmGeneratedSummaryWithoutChangingText() {
        CaseSummaryMapper mapper = mock(CaseSummaryMapper.class);
        when(mapper.confirm(9L, 40L, 7L)).thenReturn(1);
        when(mapper.selectDetail(9L, 40L)).thenReturn(result());
        CaseSummaryService service = service(mapper, mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));

        var response = service.confirm(7L, 9L, 40L);

        assertEquals("Generated summary", response.summaryText());
        verify(mapper).confirm(9L, 40L, 7L);
    }

    private CaseSummaryService service(CaseSummaryMapper mapper, CaseAccessService accessService,
                                       ApplicationEventPublisher publisher) {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setEnabled(true);
        properties.setAllowExternalCaseData(true);
        properties.setModel("deepseek-v4-flash");
        return new CaseSummaryService(mapper, accessService, properties, publisher,
                mock(AuditService.class), new ObjectMapper());
    }

    private CaseSummarySource source() {
        return new CaseSummarySource(12L, "SUCCESS", "source text");
    }

    private CaseSummaryResult result() {
        CaseSummaryResult result = new CaseSummaryResult();
        result.setSummaryId(40L);
        result.setCaseId(9L);
        result.setSummaryType("FULL");
        result.setSummaryText("Generated summary");
        result.setVersionNo(2);
        result.setCurrent(true);
        result.setGenerationParametersJson("{}");
        return result;
    }
}
