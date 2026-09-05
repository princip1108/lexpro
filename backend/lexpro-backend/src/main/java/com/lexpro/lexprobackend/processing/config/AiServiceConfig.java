package com.lexpro.lexprobackend.processing.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiServiceConfig {

    @Bean
    @Qualifier("aiServiceRestClient")
    RestClient aiServiceRestClient(AiServiceProperties properties) {
        validate(properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory);
        if (!properties.getInternalToken().isBlank()) {
            builder.defaultHeader("X-LexPro-Internal-Token", properties.getInternalToken());
        }
        return builder.build();
    }

    private void validate(AiServiceProperties properties) {
        URI baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.getHost() == null
                || (!"http".equalsIgnoreCase(baseUrl.getScheme())
                && !"https".equalsIgnoreCase(baseUrl.getScheme()))
                || baseUrl.getUserInfo() != null || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                || (baseUrl.getPath() != null && !baseUrl.getPath().isBlank() && !"/".equals(baseUrl.getPath()))) {
            throw new IllegalStateException("Invalid lexpro.ai-service base URL");
        }
        if (properties.getConnectTimeout() == null || properties.getConnectTimeout().isZero()
                || properties.getConnectTimeout().isNegative()
                || properties.getReadTimeout() == null || properties.getReadTimeout().isZero()
                || properties.getReadTimeout().isNegative()
                || properties.getMaxFileSize() == null || properties.getMaxFileSize().toBytes() <= 0
                || properties.getMaxFileSize().toBytes() > DataSizeLimit.MAX_BYTES) {
            throw new IllegalStateException("Invalid lexpro.ai-service limits");
        }
        if (properties.isEnabled()
                && (properties.getInternalToken() == null || properties.getInternalToken().length() < 32)) {
            throw new IllegalStateException("lexpro.ai-service internal token must contain at least 32 characters");
        }
    }

    private static final class DataSizeLimit {
        private static final long MAX_BYTES = 200L * 1024 * 1024;
    }
}
