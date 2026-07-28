package com.lexpro.lexprobackend.common.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestIdFilterTests {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void shouldKeepSafeClientRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "client-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("client-request-123", request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME));
        assertEquals("client-request-123", response.getHeader(RequestIdFilter.HEADER_NAME));
    }

    @Test
    void shouldReplaceUnsafeClientRequestId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "bad id with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(generated);
        assertNotEquals("bad id with spaces", generated);
        assertEquals(generated, request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME));
    }

    @Test
    void shouldExposeRequestIdInMdcOnlyDuringRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdFilter.HEADER_NAME, "mdc-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) ->
                        assertEquals("mdc-request-123", MDC.get("requestId"))
        );

        assertNull(MDC.get("requestId"));
    }
}
