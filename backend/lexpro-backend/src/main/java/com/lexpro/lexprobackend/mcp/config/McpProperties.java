package com.lexpro.lexprobackend.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "lexpro.mcp")
public class McpProperties {

    private boolean enabled;
    private String endpoint = "/mcp";
    private Duration requestTimeout = Duration.ofSeconds(55);
    private int maxItems = 20;
    private int maxOutputChars = 50_000;
    private String clientRegistryPath = "";
    private Duration tokenReloadInterval = Duration.ofSeconds(30);
    private int maxConcurrentRequests = 8;
    private int maxConcurrentPerClient = 2;
    private int rateLimitPerMinute = 60;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public int getMaxItems() { return maxItems; }
    public void setMaxItems(int maxItems) { this.maxItems = maxItems; }
    public int getMaxOutputChars() { return maxOutputChars; }
    public void setMaxOutputChars(int maxOutputChars) { this.maxOutputChars = maxOutputChars; }
    public String getClientRegistryPath() { return clientRegistryPath; }
    public void setClientRegistryPath(String clientRegistryPath) { this.clientRegistryPath = clientRegistryPath; }
    public Duration getTokenReloadInterval() { return tokenReloadInterval; }
    public void setTokenReloadInterval(Duration tokenReloadInterval) { this.tokenReloadInterval = tokenReloadInterval; }
    public int getMaxConcurrentRequests() { return maxConcurrentRequests; }
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }
    public int getMaxConcurrentPerClient() { return maxConcurrentPerClient; }
    public void setMaxConcurrentPerClient(int maxConcurrentPerClient) {
        this.maxConcurrentPerClient = maxConcurrentPerClient;
    }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public void validate() {
        if (endpoint == null || !endpoint.startsWith("/") || endpoint.length() > 100) {
            throw new IllegalStateException("lexpro.mcp.endpoint must be an absolute path of at most 100 characters");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                || requestTimeout.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalStateException("lexpro.mcp.request-timeout must be greater than PT0S and at most PT60S");
        }
        if (maxItems < 1 || maxItems > 100) {
            throw new IllegalStateException("lexpro.mcp.max-items must be between 1 and 100");
        }
        if (maxOutputChars < 1_000 || maxOutputChars > 200_000) {
            throw new IllegalStateException("lexpro.mcp.max-output-chars must be between 1000 and 200000");
        }
        if (clientRegistryPath == null || clientRegistryPath.isBlank()) {
            throw new IllegalStateException("lexpro.mcp.client-registry-path is required when MCP is enabled");
        }
        if (tokenReloadInterval == null || tokenReloadInterval.compareTo(Duration.ofSeconds(5)) < 0
                || tokenReloadInterval.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalStateException("lexpro.mcp.token-reload-interval must be between PT5S and PT10M");
        }
        if (maxConcurrentRequests < 1 || maxConcurrentRequests > 100) {
            throw new IllegalStateException("lexpro.mcp.max-concurrent-requests must be between 1 and 100");
        }
        if (maxConcurrentPerClient < 1 || maxConcurrentPerClient > maxConcurrentRequests) {
            throw new IllegalStateException(
                    "lexpro.mcp.max-concurrent-per-client must be between 1 and the global concurrency limit");
        }
        if (rateLimitPerMinute < 1 || rateLimitPerMinute > 10_000) {
            throw new IllegalStateException("lexpro.mcp.rate-limit-per-minute must be between 1 and 10000");
        }
    }
}
