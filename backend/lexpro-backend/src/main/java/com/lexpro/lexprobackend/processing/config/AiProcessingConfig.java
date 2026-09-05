package com.lexpro.lexprobackend.processing.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AiProcessingProperties.class)
public class AiProcessingConfig {

    @Bean
    @Qualifier("aiRestClient")
    RestClient aiRestClient(AiProcessingProperties properties) {
        validate(properties);
        HttpClient httpClient = HttpClient.newBuilder()
                // The tunneled vLLM HTTP server rejects h2c upgrades with a missing-body error.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private void validate(AiProcessingProperties properties) {
        if (properties.getBaseUrl() == null
                || properties.getConnectTimeout() == null || properties.getConnectTimeout().isNegative()
                || properties.getConnectTimeout().isZero()
                || properties.getReadTimeout() == null || properties.getReadTimeout().isNegative()
                || properties.getReadTimeout().isZero()
                || properties.getMaxInputChars() <= 0 || properties.getMaxOutputTokens() <= 0) {
            throw new IllegalStateException("Invalid lexpro.ai configuration");
        }
        String scheme = properties.getBaseUrl().getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("lexpro.ai.base-url must use HTTP or HTTPS");
        }
        if (properties.isEnabled()
                && (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getModel() == null || properties.getModel().isBlank())) {
            throw new IllegalStateException("Enabled AI processing requires an API key and model");
        }
    }
}
