package com.lexpro.lexprobackend.recommendation.partner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

@Component
public class PartnerTypicalCaseClient {

    private final RestClient analyzeRestClient;
    private final RestClient searchRestClient;
    private final ObjectMapper objectMapper;

    public PartnerTypicalCaseClient(
            @Qualifier("partnerTypicalCaseAnalyzeRestClient") RestClient analyzeRestClient,
            @Qualifier("partnerTypicalCaseSearchRestClient") RestClient searchRestClient,
            ObjectMapper objectMapper) {
        this.analyzeRestClient = analyzeRestClient;
        this.searchRestClient = searchRestClient;
        this.objectMapper = objectMapper;
    }

    public PartnerTypicalCaseContract.AnalyzeEnvelope analyze(PartnerTypicalCaseContract.AnalyzeRequest request) {
        byte[] response = post(analyzeRestClient, "/api/v1/typical-cases/analyze", request, false);
        return parse(response, PartnerTypicalCaseContract.AnalyzeEnvelope.class);
    }

    public PartnerTypicalCaseContract.SearchEnvelope search(PartnerTypicalCaseContract.SearchRequest request) {
        byte[] response = post(searchRestClient, "/api/v1/typical-cases/search", request, true);
        return parse(response, PartnerTypicalCaseContract.SearchEnvelope.class);
    }

    private byte[] post(RestClient client, String path, Object request, boolean analysisCanExpire) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(request);
            byte[] response = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(body.length)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            if (response == null || response.length == 0 || response.length > 10_000_000) {
                throw new PartnerTypicalCaseClientException(
                        PartnerTypicalCaseClientException.Kind.RESPONSE_INVALID, null);
            }
            return response;
        } catch (HttpClientErrorException.NotFound exception) {
            PartnerTypicalCaseClientException.Kind kind = analysisCanExpire
                    ? PartnerTypicalCaseClientException.Kind.ANALYSIS_EXPIRED
                    : PartnerTypicalCaseClientException.Kind.RESPONSE_INVALID;
            throw new PartnerTypicalCaseClientException(kind, exception);
        } catch (HttpClientErrorException exception) {
            throw new PartnerTypicalCaseClientException(
                    PartnerTypicalCaseClientException.Kind.RESPONSE_INVALID, exception);
        } catch (HttpServerErrorException exception) {
            throw new PartnerTypicalCaseClientException(
                    PartnerTypicalCaseClientException.Kind.UNAVAILABLE, exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Partner request serialization failed", exception);
        } catch (PartnerTypicalCaseClientException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PartnerTypicalCaseClientException(
                    PartnerTypicalCaseClientException.Kind.UNAVAILABLE, exception);
        }
    }

    private <T> T parse(byte[] response, Class<T> type) {
        try {
            return objectMapper.readValue(response, type);
        } catch (IOException exception) {
            throw new PartnerTypicalCaseClientException(
                    PartnerTypicalCaseClientException.Kind.RESPONSE_INVALID, exception);
        }
    }
}
