package com.lexpro.lexprobackend.recommendation.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@Configuration
@EnableConfigurationProperties(PartnerTypicalCaseProperties.class)
public class PartnerTypicalCaseConfig {

    private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    @Bean
    @Qualifier("partnerTypicalCaseAnalyzeRestClient")
    RestClient analyzeRestClient(PartnerTypicalCaseProperties properties, RetrievalProperties retrievalProperties) {
        validate(properties, retrievalProperties);
        return restClient(properties, properties.getAnalyzeTimeout());
    }

    @Bean
    @Qualifier("partnerTypicalCaseSearchRestClient")
    RestClient searchRestClient(PartnerTypicalCaseProperties properties) {
        return restClient(properties, properties.getSearchTimeout());
    }

    private RestClient restClient(PartnerTypicalCaseProperties properties, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }

    private void validate(PartnerTypicalCaseProperties properties, RetrievalProperties retrievalProperties) {
        if (properties.getBaseUrl() == null || !positive(properties.getConnectTimeout())
                || !positive(properties.getAnalyzeTimeout()) || !positive(properties.getSearchTimeout())
                || !positive(properties.getAnalysisTtl()) || properties.getAnalysisTtl().compareTo(Duration.ofHours(1)) > 0
                || properties.getMaxResults() < 1 || properties.getMaxResults() > 100) {
            throw new IllegalStateException("Invalid lexpro.partner-typical-case configuration");
        }
        String scheme = properties.getBaseUrl().getScheme();
        String host = properties.getBaseUrl().getHost();
        if (host == null || properties.getBaseUrl().getUserInfo() != null
                || properties.getBaseUrl().getQuery() != null || properties.getBaseUrl().getFragment() != null
                || !(properties.getBaseUrl().getPath() == null || properties.getBaseUrl().getPath().isBlank()
                || "/".equals(properties.getBaseUrl().getPath()))) {
            throw new IllegalStateException("Partner typical-case base URL must be an origin URL");
        }
        if (!"https".equalsIgnoreCase(scheme)
                && !("http".equalsIgnoreCase(scheme) && LOOPBACK_HOSTS.contains(host.toLowerCase()))) {
            throw new IllegalStateException("Plain HTTP is allowed only for a loopback partner typical-case URL");
        }
        if (retrievalProperties.getProvider() == TypicalCaseProvider.PARTNER
                && properties.getTokenSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("PARTNER provider requires a token secret of at least 32 bytes");
        }
    }

    private boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
