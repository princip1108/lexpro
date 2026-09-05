package com.lexpro.lexprobackend.recommendation.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RetrievalProperties.class)
public class RetrievalConfig {

    @Bean
    @Qualifier("retrievalRestClient")
    RestClient retrievalRestClient(RetrievalProperties properties) {
        validate(properties);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeout());
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    private void validate(RetrievalProperties properties) {
        if (properties.getProvider() == null || properties.getBaseUrl() == null
                || properties.getConnectTimeout() == null || properties.getConnectTimeout().isZero()
                || properties.getConnectTimeout().isNegative()
                || properties.getReadTimeout() == null || properties.getReadTimeout().isZero()
                || properties.getReadTimeout().isNegative()
                || properties.getRetries() < 0 || properties.getRetries() > 2
                || properties.getDimension() != 1024
                || !"cosine".equals(properties.getDistance())
                || !"BAAI/bge-m3".equals(properties.getModelName())) {
            throw new IllegalStateException("Invalid lexpro.retrieval configuration");
        }
        String scheme = properties.getBaseUrl().getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalStateException("lexpro.retrieval.base-url must use HTTP or HTTPS");
        }
    }
}
