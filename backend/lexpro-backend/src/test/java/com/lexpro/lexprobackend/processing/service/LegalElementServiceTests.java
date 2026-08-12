package com.lexpro.lexprobackend.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.casework.service.CaseAccessService;
import com.lexpro.lexprobackend.common.audit.AuditService;
import com.lexpro.lexprobackend.processing.ai.LegalElementJsonValidator;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.LegalElementResult;
import com.lexpro.lexprobackend.processing.domain.LegalElementSource;
import com.lexpro.lexprobackend.processing.mapper.LegalElementMapper;
import com.lexpro.lexprobackend.processing.web.dto.ConfirmLegalElementRequest;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalElementServiceTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldStartAuthorizedLegalElementJob() {
        LegalElementMapper mapper = mock(LegalElementMapper.class);
        CaseAccessService accessService = mock(CaseAccessService.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        when(mapper.lockSource(9L, 12L)).thenReturn(source());
        LegalElementService service = service(mapper, accessService, publisher);

        var response = service.start(7L, 9L, 12L, "http-1");

        assertEquals("PROCESSING", response.status());
        assertNotNull(response.requestId());
        verify(accessService).requireEdit(9L, 7L);
        verify(publisher).publishEvent(any(LegalElementRequestedEvent.class));
    }

    @Test
    void shouldStoreConfirmedElementsWithoutOverwritingOriginal() throws Exception {
        LegalElementMapper mapper = mock(LegalElementMapper.class);
        when(mapper.selectSource(9L, 12L)).thenReturn(source());
        when(mapper.confirm(eq(9L), eq(12L), eq(30L), anyString(), eq(7L))).thenReturn(1);
        when(mapper.selectDetail(9L, 12L, 30L)).thenReturn(result());
        LegalElementService service = service(mapper, mock(CaseAccessService.class),
                mock(ApplicationEventPublisher.class));

        var response = service.confirm(7L, 9L, 12L, 30L, new ConfirmLegalElementRequest(
                objectMapper.readTree(finalJson())));

        assertEquals("FACT", response.finalElements().at("/elements/0/code").textValue());
        verify(mapper).confirm(eq(9L), eq(12L), eq(30L), anyString(), eq(7L));
    }

    private LegalElementService service(LegalElementMapper mapper, CaseAccessService accessService,
                                        ApplicationEventPublisher publisher) {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setEnabled(true);
        properties.setAllowExternalCaseData(true);
        properties.setModel("deepseek-v4-flash");
        return new LegalElementService(mapper, accessService, properties, new LegalElementJsonValidator(),
                publisher, mock(AuditService.class), objectMapper);
    }

    private LegalElementSource source() {
        return new LegalElementSource(12L, 9L, 5L, "SUCCESS", "Li transferred 100 yuan.", "Fraud");
    }

    private LegalElementResult result() {
        LegalElementResult result = new LegalElementResult();
        result.setElementResultId(30L);
        result.setDocId(12L);
        result.setCaseId(9L);
        result.setRawElementsJson(finalJson());
        result.setFinalElementsJson(finalJson());
        result.setValidationReportJson("{}");
        result.setGenerationParametersJson("{}");
        return result;
    }

    private String finalJson() {
        return "{\"elements\":[{\"code\":\"FACT\",\"name\":\"Transfer\","
                + "\"content\":\"A transfer occurred\",\"evidence\":[{\"quote\":\"transferred 100 yuan\"}]}]}";
    }
}
