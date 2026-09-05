package com.lexpro.lexprobackend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "lexpro.partner-typical-case")
public class PartnerTypicalCaseProperties {

    private URI baseUrl = URI.create("http://127.0.0.1:8000");
    private boolean allowCaseData;
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration analyzeTimeout = Duration.ofSeconds(60);
    private Duration searchTimeout = Duration.ofSeconds(30);
    private int maxResults = 20;
    private Duration analysisTtl = Duration.ofMinutes(10);
    private String tokenSecret = "";

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public boolean isAllowCaseData() { return allowCaseData; }
    public void setAllowCaseData(boolean allowCaseData) { this.allowCaseData = allowCaseData; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getAnalyzeTimeout() { return analyzeTimeout; }
    public void setAnalyzeTimeout(Duration analyzeTimeout) { this.analyzeTimeout = analyzeTimeout; }
    public Duration getSearchTimeout() { return searchTimeout; }
    public void setSearchTimeout(Duration searchTimeout) { this.searchTimeout = searchTimeout; }
    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    public Duration getAnalysisTtl() { return analysisTtl; }
    public void setAnalysisTtl(Duration analysisTtl) { this.analysisTtl = analysisTtl; }
    public String getTokenSecret() { return tokenSecret; }
    public void setTokenSecret(String tokenSecret) { this.tokenSecret = tokenSecret; }
}
