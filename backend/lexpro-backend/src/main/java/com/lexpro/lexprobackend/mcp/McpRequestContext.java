package com.lexpro.lexprobackend.mcp;

import io.modelcontextprotocol.common.McpTransportContext;

import java.util.Set;

public record McpRequestContext(
        PrincipalType principalType,
        String clientId,
        Long userId,
        Set<String> authorities,
        String requestId
) {

    public static final String CONTEXT_KEY = "lexpro.mcp.request-context";

    public McpRequestContext {
        if (principalType == null || clientId == null || clientId.isBlank() || requestId == null
                || requestId.isBlank()) {
            throw new IllegalArgumentException("MCP caller identity is incomplete");
        }
        authorities = Set.copyOf(authorities);
    }

    public static McpRequestContext from(McpTransportContext context) {
        Object value = context.get(CONTEXT_KEY);
        if (value instanceof McpRequestContext requestContext) {
            return requestContext;
        }
        throw new IllegalStateException("Authenticated MCP request context is missing");
    }

    public enum PrincipalType {
        SERVICE
    }
}
