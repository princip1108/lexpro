package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.parser.DocumentProcessingException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Component
public class HttpAiServiceDocumentClient implements AiServiceDocumentClient {

    private static final String EXPECTED_SCHEMA = "lexpro.parse.v2";
    private static final String EXPECTED_OFFSET_UNIT = "UTF16_CODE_UNIT";
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpAiServiceDocumentClient(@Qualifier("aiServiceRestClient") RestClient restClient,
                                       ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiServiceParsedDocument parse(String fileName, String contentType, byte[] content, String requestId) {
        try {
            byte[] response = restClient.post()
                    .uri(builder -> builder.path("/internal/v1/documents/parse")
                            .queryParam("fileName", fileName).build())
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("X-Request-ID", requestId)
                    .body(content)
                    .retrieve()
                    .body(byte[].class);
            if (response == null || response.length == 0) {
                throw invalidResponse(null);
            }
            return validateResponse(response);
        } catch (RestClientResponseException exception) {
            String code = exception.getStatusCode().is5xxServerError()
                    ? "MINERU_SERVICE_UNAVAILABLE" : "MINERU_SERVICE_REJECTED_REQUEST";
            try {
                JsonNode error = objectMapper.readTree(exception.getResponseBodyAsByteArray());
                String detail = error == null ? "" : error.path("detail").asText();
                if (Set.of("WORD_DOCUMENT_INVALID", "WORD_DOCUMENT_TOO_LARGE", "WORD_DOCUMENT_EMPTY",
                        "WORD_EXTERNAL_CONTENT_UNSUPPORTED", "WORD_IMAGE_FORMAT_UNSUPPORTED",
                        "WORD_EMBEDDED_CONTENT_UNSUPPORTED", "LEGACY_WORD_CONVERSION_REQUIRED",
                        "DOCUMENT_TOO_LARGE", "UNSUPPORTED_DOCUMENT_TYPE").contains(detail)) {
                    code = detail;
                }
            } catch (IOException ignored) {
                // Never expose arbitrary upstream bodies, filenames, paths or credentials.
            }
            throw new DocumentProcessingException(code, "Document parsing failed", exception);
        } catch (RestClientException exception) {
            throw new DocumentProcessingException("MINERU_SERVICE_UNAVAILABLE",
                    "The approved document parser is unavailable", exception);
        }
    }

    AiServiceParsedDocument validateResponse(byte[] response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isObject()
                    || !EXPECTED_SCHEMA.equals(text(root, "schemaVersion"))
                    || !EXPECTED_OFFSET_UNIT.equals(text(root, "offsetUnit"))
                    || !Set.of("mineru", "docx").contains(text(root, "parser") == null ? "" : text(root, "parser"))) {
                throw invalidResponse(null);
            }
            String parserVersion = text(root, "parserVersion");
            String sourceText = text(root, "text");
            String expectedHash = text(root, "textSha256");
            JsonNode blocks = root.get("blocks");
            if (parserVersion == null || parserVersion.isBlank() || sourceText == null || sourceText.isBlank()
                    || expectedHash == null || !expectedHash.equals(sha256(sourceText))
                    || blocks == null || !blocks.isArray() || blocks.isEmpty()) {
                throw invalidResponse(null);
            }
            validateUnicode(sourceText);
            validateBlocks(sourceText, blocks);
            return new AiServiceParsedDocument(sourceText, objectMapper.writeValueAsString(root), parserVersion);
        } catch (IOException exception) {
            throw invalidResponse(exception);
        }
    }

    private void validateBlocks(String sourceText, JsonNode blocks) {
        Set<String> blockIds = new HashSet<>();
        int previousEnd = -2;
        int expectedOrder = 0;
        for (JsonNode block : blocks) {
            String blockId = text(block, "blockId");
            String blockText = text(block, "text");
            JsonNode orderNode = block.get("order");
            JsonNode startNode = block.get("globalStartUtf16");
            JsonNode endNode = block.get("globalEndUtf16");
            if (blockId == null || blockId.isBlank() || blockText == null
                    || orderNode == null || !orderNode.canConvertToInt()
                    || startNode == null || !startNode.canConvertToInt()
                    || endNode == null || !endNode.canConvertToInt()) {
                throw invalidResponse(null);
            }
            int order = orderNode.intValue();
            int start = startNode.intValue();
            int end = endNode.intValue();
            if (!blockIds.add(blockId) || order != expectedOrder
                    || start < 0 || end <= start || end > sourceText.length()
                    || !sourceText.substring(start, end).equals(blockText)) {
                throw invalidResponse(null);
            }
            if (previousEnd == -2 && start != 0) {
                throw invalidResponse(null);
            }
            if (previousEnd >= 0
                    && (start != previousEnd + 2 || !"\n\n".equals(sourceText.substring(previousEnd, start)))) {
                throw invalidResponse(null);
            }
            previousEnd = end;
            expectedOrder++;
        }
        if (previousEnd != sourceText.length()) {
            throw invalidResponse(null);
        }
    }

    private void validateUnicode(String text) {
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\u0000' || current == '\ufffd') {
                throw invalidResponse(null);
            }
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    throw invalidResponse(null);
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw invalidResponse(null);
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private DocumentProcessingException invalidResponse(Throwable cause) {
        return new DocumentProcessingException("MINERU_RESPONSE_INVALID",
                "The approved document parser returned an invalid positioning contract", cause);
    }
}
