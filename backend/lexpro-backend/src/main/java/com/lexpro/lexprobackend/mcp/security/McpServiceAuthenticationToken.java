package com.lexpro.lexprobackend.mcp.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class McpServiceAuthenticationToken extends AbstractAuthenticationToken {

    private final String clientId;

    public McpServiceAuthenticationToken(McpClientIdentity identity) {
        super(identity.authorities().stream().map(SimpleGrantedAuthority::new).toList());
        this.clientId = identity.clientId();
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return clientId;
    }

    @Override
    public String getName() {
        return clientId;
    }

    public String getClientId() {
        return clientId;
    }
}
