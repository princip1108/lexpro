package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Component
public class InternalAiServiceEntityRecognitionClient {

    private static final String ENTITY_SCHEMA = "lexpro.entity.v2";
    private static final String OFFSET_UNIT = "UTF16_CODE_UNIT";
    private static final Set<String> ENTITY_TYPES = Set.of(
            "SUSPECT", "LOCATION", "ORGANIZATION", "TIME", "CRIME", "DRUG");
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public InternalAiServiceEntityRecognitionClient(@Qualifier("aiServiceRestClient") RestClient restClient,
                                                    ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public EntityRecognitionOutput recognize(String text, String parsedTextJson, String requestId) {
        ObjectNode request = buildRequest(text, parsedTextJson, requestId);
        try {
            byte[] response = restClient.post()
                    .uri("/internal/v1/entities/recognize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-ID", requestId)
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            if (response == null || response.length == 0) {
                throw invalid("ENTITY_RESPONSE_EMPTY", null);
            }
            return validateResponse(response, request);
        } catch (RestClientResponseException exception) {
            throw new AiClientException("AI_PROVIDER_UNAVAILABLE", "LEXPRO_SERVICE_REJECTED_REQUEST",
                    "The internal LexPro service rejected the request", exception);
        } catch (RestClientException exception) {
            throw new AiClientException("AI_PROVIDER_UNAVAILABLE", "LEXPRO_SERVICE_UNAVAILABLE",
                    "The internal LexPro service is unavailable", exception);
        }
    }

    ObjectNode buildRequest(String sourceText, String parsedTextJson, String requestId) {
        if (sourceText == null || sourceText.isBlank()) {
            throw invalid("ENTITY_SOURCE_EMPTY", null);
        }
        String textHash = sha256(sourceText);
        ArrayNode blocks = blocksFromParseContract(sourceText, parsedTextJson, textHash);
        ObjectNode request = objectMapper.createObjectNode();
        request.put("requestId", requestId);
        request.put("sourceTextSha256", textHash);
        request.set("blocks", blocks);
        return request;
    }

    private ArrayNode blocksFromParseContract(String sourceText, String parsedTextJson, String textHash) {
        if (parsedTextJson != null && !parsedTextJson.isBlank()) {
            try {
                JsonNode parse = objectMapper.readTree(parsedTextJson);
                JsonNode sourceBlocks = parse.get("blocks");
                if ("lexpro.parse.v2".equals(text(parse, "schemaVersion"))
                        && sourceText.equals(text(parse, "text"))
                        && textHash.equals(text(parse, "textSha256"))
                        && sourceBlocks != null && sourceBlocks.isArray() && !sourceBlocks.isEmpty()) {
                    ArrayNode blocks = objectMapper.createArrayNode();
                    for (JsonNode sourceBlock : sourceBlocks) {
                        ObjectNode block = objectMapper.createObjectNode();
                        copyRequiredText(sourceBlock, block, "blockId");
                        copyRequiredText(sourceBlock, block, "text");
                        copyRequiredInteger(sourceBlock, block, "order");
                        copyOptional(sourceBlock, block, "pageNo");
                        copyOptional(sourceBlock, block, "bbox");
                        copyOptional(sourceBlock, block, "coordinateUnit");
                        blocks.add(block);
                    }
                    return blocks;
                }
            } catch (JsonProcessingException exception) {
                throw invalid("PARSE_POSITIONING_CONTRACT_INVALID", exception);
            }
        }
        ArrayNode blocks = objectMapper.createArrayNode();
        ObjectNode block = blocks.addObject();
        block.put("blockId", "document-000000");
        block.put("text", sourceText);
        block.put("order", 0);
        return blocks;
    }

    EntityRecognitionOutput validateResponse(byte[] response, ObjectNode request) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String modelName = text(root, "modelName");
            String modelVersion = text(root, "modelVersion");
            if (!root.isObject() || !ENTITY_SCHEMA.equals(text(root, "schemaVersion"))
                    || !OFFSET_UNIT.equals(text(root, "offsetUnit"))
                    || !request.path("sourceTextSha256").asText().equals(text(root, "sourceTextSha256"))
                    || modelName == null || modelName.isBlank()
                    || modelVersion == null || modelVersion.isBlank()) {
                throw invalid("ENTITY_ROOT_SCHEMA", null);
            }
            Map<String, BlockContract> blocks = blockIndex(request.path("blocks"));
            validateEntities(root.path("entities"), blocks);
            ObjectNode generation = objectMapper.createObjectNode();
            generation.put("transport", "internal-ai-service");
            generation.put("offsetUnit", OFFSET_UNIT);
            generation.put("blockCount", blocks.size());
            return new EntityRecognitionOutput(root, modelName, modelVersion,
                    "lexpro-entity-adapter-v1", ENTITY_SCHEMA,
                    "Prompt is versioned and managed by the internal AI service", generation, null);
        } catch (IOException exception) {
            throw invalid("ENTITY_RESPONSE_JSON_INVALID", exception);
        }
    }

    private Map<String, BlockContract> blockIndex(JsonNode blocks) {
        java.util.LinkedHashMap<String, BlockContract> result = new java.util.LinkedHashMap<>();
        int expectedOrder = 0;
        int globalStart = 0;
        for (JsonNode block : blocks) {
            String id = text(block, "blockId");
            String blockText = text(block, "text");
            if (id == null || blockText == null || requiredInteger(block, "order") != expectedOrder
                    || result.putIfAbsent(id, new BlockContract(blockText, globalStart)) != null) {
                throw invalid("ENTITY_BLOCK_CONTRACT_INVALID", null);
            }
            expectedOrder++;
            globalStart += blockText.length() + 2;
        }
        return result;
    }

    private void validateEntities(JsonNode entities, Map<String, BlockContract> blocks) {
        if (!entities.isArray() || entities.size() > 10_000) {
            throw invalid("ENTITY_COLLECTION_INVALID", null);
        }
        for (JsonNode entity : entities) {
            String type = text(entity, "type");
            String value = text(entity, "text");
            String blockId = text(entity, "blockId");
            BlockContract block = blocks.get(blockId);
            if (!entity.isObject() || !ENTITY_TYPES.contains(type) || value == null || value.isBlank()
                    || block == null) {
                throw invalid("ENTITY_OCCURRENCE_INVALID", null);
            }
            int blockStart = requiredInteger(entity, "blockStartUtf16");
            int blockEnd = requiredInteger(entity, "blockEndUtf16");
            int globalStart = requiredInteger(entity, "globalStartUtf16");
            int globalEnd = requiredInteger(entity, "globalEndUtf16");
            if (!exactSlice(block.text(), blockStart, blockEnd, value)
                    || globalStart != block.globalStart() + blockStart
                    || globalEnd != block.globalStart() + blockEnd) {
                throw invalid("ENTITY_OFFSET_INVALID", null);
            }
            ((ObjectNode) entity).put("startOffset", globalStart);
            ((ObjectNode) entity).put("endOffset", globalEnd);
        }
    }

    private boolean exactSlice(String source, int start, int end, String expected) {
        return source != null && start >= 0 && end > start && end <= source.length()
                && source.substring(start, end).equals(expected)
                && !splitsSurrogate(source, start) && !splitsSurrogate(source, end);
    }

    private boolean splitsSurrogate(String source, int offset) {
        return offset > 0 && offset < source.length()
                && Character.isHighSurrogate(source.charAt(offset - 1))
                && Character.isLowSurrogate(source.charAt(offset));
    }

    private int requiredInteger(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw invalid("ENTITY_OFFSET_INVALID", null);
        }
        return value.intValue();
    }

    private void copyRequiredText(JsonNode from, ObjectNode to, String field) {
        String value = text(from, field);
        if (value == null || value.isBlank()) {
            throw invalid("PARSE_POSITIONING_CONTRACT_INVALID", null);
        }
        to.put(field, value);
    }

    private void copyRequiredInteger(JsonNode from, ObjectNode to, String field) {
        to.put(field, requiredInteger(from, field));
    }

    private void copyOptional(JsonNode from, ObjectNode to, String field) {
        if (from.has(field) && !from.get(field).isNull()) {
            to.set(field, from.get(field));
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
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

    private AiClientException invalid(String diagnosticCode, Throwable cause) {
        return new AiClientException("AI_RESPONSE_INVALID", diagnosticCode,
                "The internal LexPro response failed the entity positioning contract", cause);
    }

    private record BlockContract(String text, int globalStart) {
    }
}
