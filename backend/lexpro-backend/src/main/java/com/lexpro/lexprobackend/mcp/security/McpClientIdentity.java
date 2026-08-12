package com.lexpro.lexprobackend.mcp.security;

import java.util.Set;

public record McpClientIdentity(String clientId, Set<String> authorities) {

    public McpClientIdentity {
        authorities = Set.copyOf(authorities);
    }
}
