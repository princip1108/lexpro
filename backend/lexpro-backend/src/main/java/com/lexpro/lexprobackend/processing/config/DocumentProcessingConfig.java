package com.lexpro.lexprobackend.processing.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(DocumentProcessingProperties.class)
public class DocumentProcessingConfig {

    @Bean(name = "documentProcessingExecutor")
    Executor documentProcessingExecutor(DocumentProcessingProperties properties) {
        validate(properties);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCoreThreads());
        executor.setMaxPoolSize(properties.getMaxThreads());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("document-processing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    private void validate(DocumentProcessingProperties properties) {
        Duration staleAfter = properties.getStaleAfter();
        if (properties.getCoreThreads() <= 0
                || properties.getMaxThreads() < properties.getCoreThreads()
                || properties.getQueueCapacity() < 0
                || properties.getMaxExtractedChars() <= 0
                || staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalStateException("Invalid lexpro.processing configuration");
        }
    }
}
