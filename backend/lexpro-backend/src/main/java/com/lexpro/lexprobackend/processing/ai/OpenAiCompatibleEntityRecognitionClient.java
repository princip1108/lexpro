package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Locale;

@Component
public class OpenAiCompatibleEntityRecognitionClient implements EntityRecognitionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEntityRecognitionClient.class);
    static final String PROMPT_VERSION = "entity-recognition-v3";
    static final String SCHEMA_VERSION = "entity-result-v1";
    private static final Set<String> ENTITY_TYPES = Set.of(
            "SUSPECT", "LOCATION", "ORGANIZATION", "TIME", "CRIME", "DRUG");
    static final String SYSTEM_PROMPT = """
            Extract entities only from the supplied legal-document text. Never infer missing facts.
            Return one JSON object and no Markdown. The required format is:
            {"entities":[{"type":"SUSPECT|LOCATION|ORGANIZATION|TIME|CRIME|DRUG","text":"exact source text","normalizedText":null,"confidence":0.0}]}
            Do not return source offsets; the server calculates them after validating the exact source text.
            Confidence must be between 0 and 1.
            Every entity must occur in the source text. Do not add explanations or extra top-level fields.
            Return at most 80 of the most legally relevant entities. Do not repeat the same type and text.
            Keep text spans concise so the complete JSON object fits within the response limit.
            """;

    private final StructuredAiClient client;

    public OpenAiCompatibleEntityRecognitionClient(StructuredAiClient client) {
        this.client = client;
    }

    @Override
    public EntityRecognitionOutput recognize(String text, String requestId) {
        StructuredAiOutput output = client.generate(SYSTEM_PROMPT, text, requestId);
        JsonNode entities = output.content();
        int generatedOffsets = validateAndNormalizeEntities(entities, text);
        if (generatedOffsets > 0) {
            log.info("Generated AI entity offsets requestId={} entityCount={}", requestId, generatedOffsets);
        }
        return new EntityRecognitionOutput(entities, output.responseModel(), output.responseModel(), PROMPT_VERSION, SCHEMA_VERSION,
                SYSTEM_PROMPT, output.generationParameters(), output.tokenUsage());
    }

    private int validateAndNormalizeEntities(JsonNode root, String sourceText) {
        JsonNode entities = root == null ? null : root.get("entities");
        if (root == null || !root.isObject() || entities == null || !entities.isArray()
                || entities.size() > 1_000) {
            throw invalid("ENTITY_ROOT_SCHEMA", "AI response does not match the entity schema");
        }
        int generatedOffsets = 0;
        int searchFrom = 0;
        int entityIndex = 0;
        for (JsonNode entity : entities) {
            if (!entity.isObject() || !entity.path("type").isTextual() || entity.path("type").textValue().isBlank()
                    || !entity.path("text").isTextual() || entity.path("text").textValue().isBlank()) {
                throw invalid("ENTITY_SHAPE", "AI response contains an invalid entity at index " + entityIndex);
            }
            ObjectNode object = (ObjectNode) entity;
            String type = entity.path("type").textValue().trim().toUpperCase(Locale.ROOT);
            String entityText = entity.path("text").textValue().trim();
            object.put("type", type);
            object.put("text", entityText);
            if (!ENTITY_TYPES.contains(type)) {
                throw invalid("ENTITY_TYPE", "AI response contains an unsupported entity type at index " + entityIndex);
            }
            if (!sourceText.contains(entityText)) {
                throw invalid("ENTITY_NOT_IN_SOURCE",
                        "AI response contains an untraceable entity at index " + entityIndex);
            }
            JsonNode confidence = entity.get("confidence");
            if (confidence != null && !confidence.isNull()) {
                double value = numericConfidence(confidence, entityIndex);
                object.put("confidence", value);
            }
            searchFrom = normalizeOffsets(entity, sourceText, entityText, searchFrom);
            generatedOffsets++;
            entityIndex++;
        }
        return generatedOffsets;
    }

    private double numericConfidence(JsonNode confidence, int entityIndex) {
        double value;
        try {
            value = confidence.isNumber() ? confidence.doubleValue()
                    : Double.parseDouble(confidence.textValue());
        } catch (RuntimeException exception) {
            throw invalid("ENTITY_CONFIDENCE", "AI response contains invalid confidence at index " + entityIndex);
        }
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw invalid("ENTITY_CONFIDENCE", "AI response contains invalid confidence at index " + entityIndex);
        }
        return value;
    }

    private AiClientException invalid(String diagnosticCode, String message) {
        return new AiClientException("AI_RESPONSE_INVALID", diagnosticCode, message);
    }

    private int normalizeOffsets(JsonNode entity, String sourceText, String entityText, int searchFrom) {
        JsonNode startNode = entity.get("startOffset");
        JsonNode endNode = entity.get("endOffset");
        int preferredStart = searchFrom;
        if (startNode != null && endNode != null && startNode.canConvertToInt() && endNode.canConvertToInt()) {
            int start = startNode.intValue();
            int end = endNode.intValue();
            if (start >= 0 && end >= start && end <= sourceText.length()
                    && sourceText.substring(start, end).equals(entityText)) {
                preferredStart = start;
            }
        }
        int start = sourceText.indexOf(entityText, Math.min(preferredStart, sourceText.length()));
        if (start < 0) {
            start = sourceText.indexOf(entityText);
        }
        ObjectNode object = (ObjectNode) entity;
        object.put("startOffset", start);
        object.put("endOffset", start + entityText.length());
        return start + entityText.length();
    }

}
