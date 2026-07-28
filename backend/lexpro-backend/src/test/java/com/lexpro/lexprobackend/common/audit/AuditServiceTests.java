package com.lexpro.lexprobackend.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.common.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTests {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPersistJsonDetailAndCurrentRequestId() {
        OperationLogMapper mapper = mock(OperationLogMapper.class);
        AuditService service = new AuditService(mapper, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE_NAME, "audit-request-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AuditEvent event = new AuditEvent(
                7L,
                11L,
                "CASE_UPDATED",
                "CASE",
                "11",
                AuditResult.SUCCESS,
                Map.of("field", "status")
        );

        service.record(event);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).insert(eq(event), json.capture(), eq("audit-request-123"));
        assertEquals("{\"field\":\"status\"}", json.getValue());
    }
}
