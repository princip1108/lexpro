package com.lexpro.lexprobackend.report.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.report.domain.CaseCardField;
import com.lexpro.lexprobackend.report.domain.CaseCardJobRecord;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import com.lexpro.lexprobackend.report.mapper.CaseCardMapper;
import com.lexpro.lexprobackend.report.web.dto.CaseCardSourceRequest;
import com.lexpro.lexprobackend.report.web.dto.ConfirmCaseCardFieldRequest;
import com.lexpro.lexprobackend.report.web.dto.StartCaseCardRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaseCardServiceTests {

    @Test
    void shouldStartFromExplicitTypedSources() {
        CaseCardMapper mapper = mock(CaseCardMapper.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.selectSource(9L, "DOCUMENT", 12L))
                .thenReturn(new CaseCardSourceMaterial("DOCUMENT", 12L, 8L, "source text", true));
        when(mapper.lockCase(9L)).thenReturn(9L);
        doAnswer(invocation -> {
            invocation.<com.lexpro.lexprobackend.report.domain.CaseCardFillTask>getArgument(0).setFillTaskId(40L);
            return 1;
        }).when(mapper).insertTask(any());
        when(mapper.selectJob(org.mockito.ArgumentMatchers.eq(9L), any()))
                .thenAnswer(invocation -> new CaseCardJobRecord(invocation.getArgument(1), 40L, "AUTO",
                        "PROCESSING", null, OffsetDateTime.now(), null));
        CaseCardService service = service(mapper, accessService, publisher);

        var response = service.start(7L, 9L, new StartCaseCardRequest("AUTO",
                List.of(new CaseCardSourceRequest("DOCUMENT", 12L))), "http-1");

        assertEquals(40L, response.fillTaskId());
        assertEquals("PROCESSING", response.status());
        verify(mapper).insertSource(40L, 9L, "DOCUMENT", 12L);
        verify(publisher).publishEvent(any(CaseCardRequestedEvent.class));
    }

    @Test
    void shouldRejectDuplicateTypedSources() {
        CaseCardService service = service(mock(CaseCardMapper.class), mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));
        var source = new CaseCardSourceRequest("SUMMARY", 3L);

        ApiException exception = assertThrows(ApiException.class, () -> service.start(7L, 9L,
                new StartCaseCardRequest("HYBRID", List.of(source, source)), "http-1"));

        assertEquals("CASE_CARD_SOURCES_DUPLICATE", exception.getErrorCode());
    }

    @Test
    void shouldRejectASecondFieldConfirmation() {
        CaseCardMapper mapper = mock(CaseCardMapper.class);
        var task = new com.lexpro.lexprobackend.report.domain.CaseCardFillTask();
        task.setFillStatus("DRAFT");
        when(mapper.selectTask(9L, 40L)).thenReturn(task);
        CaseCardField field = new CaseCardField();
        field.setFieldId(5L);
        field.setFillTaskId(40L);
        field.setConfirmStatus("CONFIRMED");
        when(mapper.selectField(9L, 40L, 5L)).thenReturn(field);
        CaseCardService service = service(mapper, mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));

        ApiException exception = assertThrows(ApiException.class, () -> service.confirmField(7L, 9L, 40L, 5L,
                new ConfirmCaseCardFieldRequest("CONFIRMED", null)));

        assertEquals("CASE_CARD_FIELD_ALREADY_RESOLVED", exception.getErrorCode());
    }

    private CaseCardService service(CaseCardMapper mapper, CaseAccessService accessService,
                                    ApplicationEventPublisher publisher) {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setEnabled(true);
        properties.setAllowExternalCaseData(true);
        properties.setModel("deepseek-v4-flash");
        return new CaseCardService(mapper, accessService, properties, publisher,
                mock(AuditService.class), new ObjectMapper());
    }
}
