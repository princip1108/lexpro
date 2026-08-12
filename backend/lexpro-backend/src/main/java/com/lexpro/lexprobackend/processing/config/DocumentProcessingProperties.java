package com.lexpro.lexprobackend.processing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "lexpro.processing")
public class DocumentProcessingProperties {

    private int coreThreads = 2;
    private int maxThreads = 4;
    private int queueCapacity = 50;
    private int maxExtractedChars = 2_000_000;
    private Duration staleAfter = Duration.ofMinutes(30);

    public int getCoreThreads() { return coreThreads; }
    public void setCoreThreads(int coreThreads) { this.coreThreads = coreThreads; }
    public int getMaxThreads() { return maxThreads; }
    public void setMaxThreads(int maxThreads) { this.maxThreads = maxThreads; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getMaxExtractedChars() { return maxExtractedChars; }
    public void setMaxExtractedChars(int maxExtractedChars) { this.maxExtractedChars = maxExtractedChars; }
    public Duration getStaleAfter() { return staleAfter; }
    public void setStaleAfter(Duration staleAfter) { this.staleAfter = staleAfter; }
}
