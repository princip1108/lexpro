package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.common.error.ApiException;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionResult;
import com.lexpro.lexprobackend.processing.domain.EntityRecognitionSource;
import com.lexpro.lexprobackend.processing.mapper.EntityRecognitionMapper;
import com.lexpro.lexprobackend.processing.web.dto.ConfirmEntityRecognitionRequest;
import com.lexpro.lexprobackend.processing.web.dto.EntityRecognitionJobResponse;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EntityRecognitionServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldStartAuthorizedAsynchronousRecognition() {
        EntityRecognitionMapper mapper = mock(EntityRecognitionMapper.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.lockSource(9L, 12L)).thenReturn(source());
        EntityRecognitionService service = service(mapper, accessService, publisher, enabledProperties());

        EntityRecognitionJobResponse response = service.start(7L, 9L, 12L, "http-request-1");

        assertEquals("PROCESSING", response.status());
        assertNotNull(response.requestId());
        verify(accessService).requireEdit(9L, 7L);
        verify(publisher).publishEvent(any(EntityRecognitionRequestedEvent.class));
    }

    @Test
    void shouldBlockExternalDataUntilSeparatelyApproved() {
        AiProcessingProperties properties = enabledProperties();
        properties.setAllowExternalCaseData(false);
        EntityRecognitionService service = service(mock(EntityRecognitionMapper.class),
                mock(CaseAccessService.class), mock(ApplicationEventPublisher.class), properties);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.start(7L, 9L, 12L, "http-request-1"));

        assertEquals("AI_DATA_EXPORT_DISABLED", exception.getErrorCode());
    }

    @Test
    void shouldStoreHumanConfirmationSeparately() throws Exception {
        EntityRecognitionMapper mapper = mock(EntityRecognitionMapper.class);
        when(mapper.selectSource(9L, 12L)).thenReturn(source());
        when(mapper.confirm(eq(9L), eq(12L), eq(30L), any(String.class), eq(7L))).thenReturn(1);
        EntityRecognitionResult stored = result();
        when(mapper.selectDetail(9L, 12L, 30L)).thenReturn(stored);
        EntityRecognitionService service = service(mapper, mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class), enabledProperties());

        var response = service.confirm(7L, 9L, 12L, 30L,
                new ConfirmEntityRecognitionRequest(objectMapper.readTree(
                        "{\"entities\":[{\"type\":\"PERSON\",\"text\":\"Li\"}]}")));

        assertEquals("Li", response.finalEntities().at("/entities/0/text").textValue());
        verify(mapper).confirm(eq(9L), eq(12L), eq(30L), any(String.class), eq(7L));
    }

    private EntityRecognitionService service(EntityRecognitionMapper mapper, CaseAccessService accessService,
                                             ApplicationEventPublisher publisher,
                                             AiProcessingProperties properties) {
        return new EntityRecognitionService(mapper, accessService, properties, publisher,
                mock(AuditService.class), objectMapper);
    }

    private AiProcessingProperties enabledProperties() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setEnabled(true);
        properties.setAllowExternalCaseData(true);
        properties.setModel("deepseek-v4-flash");
        return properties;
    }

    private EntityRecognitionSource source() {
        return new EntityRecognitionSource(12L, 9L, 5L, "SUCCESS", "Li filed a claim.");
    }

    private EntityRecognitionResult result() {
        EntityRecognitionResult result = new EntityRecognitionResult();
        result.setEntityResultId(30L);
        result.setDocId(12L);
        result.setCaseId(9L);
        result.setEntitiesJson("{\"entities\":[]}");
        result.setFinalEntitiesJson("{\"entities\":[{\"type\":\"PERSON\",\"text\":\"Li\"}]}");
        result.setGenerationParametersJson("{}");
        return result;
    }
}
