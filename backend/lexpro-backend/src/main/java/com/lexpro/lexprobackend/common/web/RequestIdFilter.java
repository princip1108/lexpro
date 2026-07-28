package com.lexpro.lexprobackend.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestIdFilter.class.getName() + ".requestId";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);

        try (MDC.MDCCloseable ignored = MDC.putCloseable("requestId", requestId)) {
            long startedAt = System.nanoTime();
            try {
                filterChain.doFilter(request, response);
            } finally {
                long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
                log.info(
                        "http_request method={} path={} status={} durationMs={}",
                        request.getMethod(),
                        request.getRequestURI(),
                        response.getStatus(),
                        durationMillis
                );
            }
        }
    }

    public static String getOrCreateRequestId(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE_NAME);
        if (existing instanceof String requestId) {
            return requestId;
        }

        String requestId = newRequestId();
        request.setAttribute(ATTRIBUTE_NAME, requestId);
        return requestId;
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return newRequestId();
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
