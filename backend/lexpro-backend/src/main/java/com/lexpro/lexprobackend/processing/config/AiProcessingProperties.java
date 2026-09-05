package com.lexpro.lexprobackend.processing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "lexpro.ai")
public class AiProcessingProperties {

    private boolean enabled;
    private URI baseUrl = URI.create("https://api.deepseek.com");
    private String apiKey = "";
    private String model = "";
    private boolean allowExternalCaseData;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(60);
    private int maxInputChars = 60_000;
    private int maxOutputTokens = 4_096;
    private boolean enableThinking;
    private volatile Endpoint runtimeEndpoint;

    public record Endpoint(boolean enabled, URI baseUrl, String apiKey, String model, boolean enableThinking) {
        @Override public String toString() { return "AI endpoint [redacted]"; }
    }
    public Endpoint endpoint() {
        Endpoint current=runtimeEndpoint;
        return current==null?new Endpoint(enabled,baseUrl,apiKey,model,enableThinking):current;
    }
    public void setRuntimeEndpoint(Endpoint endpoint) { runtimeEndpoint=endpoint; }
    public boolean isEnableThinking() { return endpoint().enableThinking(); }
    public void setEnableThinking(boolean enableThinking) { this.enableThinking=enableThinking; }

    public boolean isEnabled() { return endpoint().enabled(); }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getBaseUrl() { return endpoint().baseUrl(); }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return endpoint().apiKey(); }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return endpoint().model(); }
    public void setModel(String model) { this.model = model; }
    public boolean isAllowExternalCaseData() { return allowExternalCaseData; }
    public void setAllowExternalCaseData(boolean allowExternalCaseData) {
        this.allowExternalCaseData = allowExternalCaseData;
    }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxInputChars() { return maxInputChars; }
    public void setMaxInputChars(int maxInputChars) { this.maxInputChars = maxInputChars; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
}
