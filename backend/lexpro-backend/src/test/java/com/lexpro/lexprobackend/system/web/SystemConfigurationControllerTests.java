package com.lexpro.lexprobackend.system.web;

import com.lexpro.lexprobackend.common.audit.OperationLogMapper;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import com.lexpro.lexprobackend.processing.config.AiServiceProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemConfigurationControllerTests {

    @Test
    void shouldExposeDeploymentModelWithoutSecrets() {
        AiServiceProperties internal = new AiServiceProperties();
        internal.setEnabled(true);
        internal.setBaseUrl(URI.create("http://127.0.0.1:8020"));
        internal.setInternalToken("must-not-leak");
        AiProcessingProperties external = new AiProcessingProperties();
        external.setApiKey("must-not-leak-either");
        var controller = new SystemConfigurationController(internal, external, mock(OperationLogMapper.class),
                mock(com.lexpro.lexprobackend.system.service.ModelConfigurationService.class));

        var response = controller.modelConfiguration();

        assertEquals("LexPro", response.activeMode());
        assertEquals("••••••••", response.items().getFirst().apiKeyMasked());
        assertFalse(response.toString().contains("must-not-leak"));
        external.setEnabled(true);
        external.setModel("LexPro_8B");
        external.setBaseUrl(URI.create("http://127.0.0.1:8001/v1"));
        response = controller.modelConfiguration();
        assertEquals("LexPro_8B", response.activeMode());
        assertEquals("LexPro_8B", response.items().getLast().displayName());
        assertEquals(true, response.items().getLast().active());
        assertFalse(response.toString().contains("must-not-leak"));
    }

    @Test
    void shouldClampOperationLogLimitToTwoHundred() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        when(mapper.selectRecent(200)).thenReturn(List.of(new OperationLogMapper.OperationLogRow(
                9, "CASE_UPDATE", "CASE", "12", "SUCCESS", "{}", "管理员",
                OffsetDateTime.parse("2026-09-05T04:00:00+08:00"))));
        var controller = new SystemConfigurationController(new AiServiceProperties(),
                new AiProcessingProperties(), mapper,
                mock(com.lexpro.lexprobackend.system.service.ModelConfigurationService.class));

        var response = controller.operationLogs(999);

        assertEquals(1, response.items().size());
        assertEquals("CASE #12", response.items().getFirst().target());
        verify(mapper).selectRecent(200);
    }
}
