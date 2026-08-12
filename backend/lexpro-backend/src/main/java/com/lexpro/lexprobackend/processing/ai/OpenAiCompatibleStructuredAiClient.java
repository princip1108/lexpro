package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleStructuredAiClient implements StructuredAiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleStructuredAiClient.class);
    private final RestClient restClient;
    private final AiProcessingProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleStructuredAiClient(@Qualifier("aiRestClient") RestClient restClient,
                                              AiProcessingProperties properties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public StructuredAiOutput generate(String systemPrompt, String userContent, String requestId) {
        if (!properties.isEnabled()) {
            throw new AiClientException("AI_PROVIDER_DISABLED", "AI processing is disabled");
        }
        if (systemPrompt == null || systemPrompt.isBlank() || userContent == null || userContent.isBlank()) {
            throw new IllegalArgumentException("AI prompt and user content must not be blank");
        }
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("temperature", 0);
        parameters.put("maxTokens", properties.getMaxOutputTokens());
        parameters.put("responseFormat", "json_object");
        parameters.put("thinking", "disabled");
        Map<String, Object> body = Map.of(
                "model", properties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)),
                "temperature", 0,
                "max_tokens", properties.getMaxOutputTokens(),
                "response_format", Map.of("type", "json_object"),
                "thinking", Map.of("type", "disabled")
        );
        try {
            JsonNode response = restClient.post()
                    .uri(completionEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .header("X-Request-Id", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            return parseResponse(response, parameters, requestId);
        } catch (RestClientResponseException exception) {
            String code = exception.getStatusCode().is4xxClientError()
                    ? "AI_PROVIDER_REJECTED" : "AI_PROVIDER_UNAVAILABLE";
            throw new AiClientException(code, "AI provider returned HTTP " + exception.getStatusCode().value(),
                    exception);
        } catch (RestClientException exception) {
            throw new AiClientException("AI_PROVIDER_UNAVAILABLE", "AI provider request failed", exception);
        }
    }

    private StructuredAiOutput parseResponse(JsonNode response, JsonNode parameters, String requestId) {
        JsonNode messageContent = response == null ? null : response.at("/choices/0/message/content");
        String finishReason = response == null ? null : response.at("/choices/0/finish_reason").textValue();
        int contentChars = messageContent != null && messageContent.isTextual()
                ? messageContent.textValue().length() : 0;
        if ("length".equals(finishReason)) {
            log.warn("structured_ai_response_invalid requestId={} category=OUTPUT_TRUNCATED finishReason={} contentChars={}",
                    requestId, finishReason, contentChars);
            throw invalid("OUTPUT_TRUNCATED", "AI response was truncated at the output-token limit");
        }
        if (messageContent == null || !messageContent.isTextual() || messageContent.textValue().isBlank()) {
            log.warn("structured_ai_response_invalid requestId={} category=MISSING_CONTENT finishReason={} contentChars={}",
                    requestId, finishReason, contentChars);
            throw invalid("MISSING_CONTENT", "AI response does not contain message content");
        }
        JsonNode content;
        try {
            content = objectMapper.readTree(stripCodeFence(messageContent.textValue()));
        } catch (JsonProcessingException exception) {
            log.warn("structured_ai_response_invalid requestId={} category=INVALID_JSON finishReason={} contentChars={}",
                    requestId, finishReason, contentChars);
            throw invalid("INVALID_JSON", "AI response content is not valid JSON", exception);
        }
        if (content == null || !content.isObject()) {
            log.warn("structured_ai_response_invalid requestId={} category=ROOT_NOT_OBJECT finishReason={} contentChars={}",
                    requestId, finishReason, contentChars);
            throw invalid("ROOT_NOT_OBJECT", "AI response content must be a JSON object");
        }
        log.info("structured_ai_response_received requestId={} finishReason={} contentChars={}",
                requestId, finishReason, contentChars);
        JsonNode usage = response.get("usage");
        String responseModel = response.path("model").isTextual() ? response.path("model").textValue() : null;
        return new StructuredAiOutput(content, responseModel, parameters,
                usage == null || usage.isNull() ? null : usage);
    }

    private AiClientException invalid(String diagnosticCode, String message) {
        return new AiClientException("AI_RESPONSE_INVALID", diagnosticCode, message);
    }

    private AiClientException invalid(String diagnosticCode, String message, Throwable cause) {
        return new AiClientException("AI_RESPONSE_INVALID", diagnosticCode, message, cause);
    }

    private String completionEndpoint() {
        return properties.getBaseUrl().toString().replaceAll("/+$", "") + "/chat/completions";
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            return trimmed;
        }
        return trimmed.substring(firstLineEnd + 1, closingFence).trim();
    }
}
