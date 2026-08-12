package com.lexpro.lexprobackend.recommendation.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.recommendation.config.RetrievalProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Supplier;

@Component
public class PythonRetrievalClient implements RetrievalClient {

    private final RestClient restClient;
    private final RetrievalProperties properties;
    private final ObjectMapper objectMapper;

    public PythonRetrievalClient(@Qualifier("retrievalRestClient") RestClient restClient,
                                 RetrievalProperties properties,
                                 ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public RetrievalContract.NormalizeResponse normalize(RetrievalContract.NormalizeRequest request) {
        byte[] body = jsonBytes(request);
        return execute("RETRIEVAL_NORMALIZATION_UNAVAILABLE", () -> restClient.post()
                .uri("/internal/v1/normalize")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .body(RetrievalContract.NormalizeResponse.class));
    }

    @Override
    public RetrievalContract.RetrieveResponse retrieve(RetrievalContract.RetrieveRequest request) {
        byte[] body = jsonBytes(request);
        return execute("RETRIEVAL_SERVICE_UNAVAILABLE", () -> restClient.post()
                .uri("/internal/v1/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .body(RetrievalContract.RetrieveResponse.class));
    }

    private <T> T execute(String errorCode, Supplier<T> operation) {
        RestClientException last = null;
        for (int attempt = 0; attempt <= properties.getRetries(); attempt++) {
            try {
                T response = operation.get();
                if (response == null) {
                    throw new RestClientException("Retrieval service returned an empty response");
                }
                return response;
            } catch (HttpClientErrorException exception) {
                throw new RetrievalClientException("RETRIEVAL_REQUEST_REJECTED", exception);
            } catch (RestClientException exception) {
                last = exception;
            }
        }
        throw new RetrievalClientException(errorCode, last);
    }

    private byte[] jsonBytes(Object request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new RetrievalClientException("RETRIEVAL_REQUEST_SERIALIZATION_FAILED", exception);
        }
    }
}
