package com.lexpro.lexprobackend.mcp.security;

import com.lexpro.lexprobackend.auth.config.SecurityProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public final class McpServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final McpClientRegistry registry;
    private final McpRequestRateLimiter rateLimiter;
    private final SecurityProblemWriter problemWriter;

    public McpServiceTokenAuthenticationFilter(McpClientRegistry registry, McpRequestRateLimiter rateLimiter,
                                               SecurityProblemWriter problemWriter) {
        this.registry = registry;
        this.rateLimiter = rateLimiter;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<McpClientIdentity> identity = authenticate(authorization);
        if (identity.isPresent()) {
            if (!rateLimiter.allow(identity.get().clientId())) {
                response.setHeader("Retry-After", "60");
                problemWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests", "MCP_RATE_LIMITED", "The MCP client request limit was exceeded");
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new McpServiceAuthenticationToken(identity.get()));
        }
        filterChain.doFilter(request, response);
    }

    private Optional<McpClientIdentity> authenticate(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                || authorization.length() == BEARER_PREFIX.length()) {
            return Optional.empty();
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        if (!token.equals(token.trim()) || token.contains(" ")) {
            return Optional.empty();
        }
        return registry.authenticate(token);
    }
}
