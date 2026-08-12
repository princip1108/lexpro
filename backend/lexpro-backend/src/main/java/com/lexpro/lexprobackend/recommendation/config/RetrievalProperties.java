package com.lexpro.lexprobackend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("lexpro.retrieval")
public class RetrievalProperties {

    private boolean enabled;
    private URI baseUrl = URI.create("http://127.0.0.1:8010");
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration readTimeout = Duration.ofSeconds(20);
    private int retries = 1;
    private String modelName = "BAAI/bge-m3";
    private String modelVersion = "main";
    private int dimension = 1024;
    private String distance = "cosine";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getRetries() { return retries; }
    public void setRetries(int retries) { this.retries = retries; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }
}
