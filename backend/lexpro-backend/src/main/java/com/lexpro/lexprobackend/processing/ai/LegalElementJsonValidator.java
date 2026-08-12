package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Locale;

@Component
public class LegalElementJsonValidator {

    private static final Set<String> ELEMENT_CODES = Set.of("SUBJECT", "SUBJECTIVE", "OBJECT", "OBJECTIVE",
            "FACT", "EVIDENCE", "LEGAL_BASIS", "DISPUTE_FOCUS", "OTHER");

    public void validate(JsonNode root, String sourceText) {
        JsonNode elements = root == null ? null : root.get("elements");
        if (root == null || !root.isObject() || elements == null || !elements.isArray()
                || elements.isEmpty() || elements.size() > 200) {
            throw new IllegalArgumentException("Legal-element output must contain a non-empty elements array");
        }
        JsonNode validation = root.get("validation");
        if (validation != null && !validation.isObject()) {
            throw new IllegalArgumentException("Legal-element validation report must be an object");
        }
        int elementIndex = 0;
        for (JsonNode element : elements) {
            validateElement(element, sourceText, elementIndex++);
        }
    }

    private void validateElement(JsonNode element, String sourceText, int elementIndex) {
        if (!element.isObject() || !element.path("code").isTextual()
                || !element.path("name").isTextual() || element.path("name").textValue().isBlank()
                || !element.path("content").isTextual() || element.path("content").textValue().isBlank()) {
            throw new IllegalArgumentException("ELEMENT_SHAPE:index=" + elementIndex);
        }
        ObjectNode object = (ObjectNode) element;
        String code = element.path("code").textValue().trim().toUpperCase(Locale.ROOT);
        object.put("code", code);
        if (!ELEMENT_CODES.contains(code)) {
            throw new IllegalArgumentException("ELEMENT_CODE:index=" + elementIndex);
        }
        JsonNode confidence = element.get("confidence");
        if (confidence != null && !confidence.isNull()) {
            double value = numericConfidence(confidence, elementIndex);
            object.put("confidence", value);
        }
        JsonNode evidence = element.get("evidence");
        if (evidence == null || !evidence.isArray() || evidence.isEmpty() || evidence.size() > 100) {
            throw new IllegalArgumentException("ELEMENT_EVIDENCE:index=" + elementIndex);
        }
        int evidenceIndex = 0;
        for (JsonNode item : evidence) {
            validateEvidence(item, sourceText, elementIndex, evidenceIndex++);
        }
    }

    private void validateEvidence(JsonNode evidence, String sourceText, int elementIndex, int evidenceIndex) {
        if (!evidence.isObject() || !evidence.path("quote").isTextual()
                || evidence.path("quote").textValue().isBlank()) {
            throw new IllegalArgumentException("EVIDENCE_SHAPE:index=" + elementIndex + ":" + evidenceIndex);
        }
        ObjectNode object = (ObjectNode) evidence;
        String quote = evidence.path("quote").textValue().trim();
        object.put("quote", quote);
        if (sourceText == null || !sourceText.contains(quote)) {
            throw new IllegalArgumentException("EVIDENCE_NOT_IN_SOURCE:index=" + elementIndex + ":" + evidenceIndex);
        }
        JsonNode startNode = evidence.get("startOffset");
        JsonNode endNode = evidence.get("endOffset");
        if (startNode == null && endNode == null) {
            return;
        }
        if (startNode == null || endNode == null || !startNode.canConvertToInt() || !endNode.canConvertToInt()) {
            throw new IllegalArgumentException("EVIDENCE_OFFSETS:index=" + elementIndex + ":" + evidenceIndex);
        }
        int start = startNode.intValue();
        int end = endNode.intValue();
        if (start < 0 || end < start || end > sourceText.length()
                || !sourceText.substring(start, end).equals(quote)) {
            throw new IllegalArgumentException("EVIDENCE_OFFSETS:index=" + elementIndex + ":" + evidenceIndex);
        }
    }

    private double numericConfidence(JsonNode confidence, int elementIndex) {
        double value;
        try {
            value = confidence.isNumber() ? confidence.doubleValue()
                    : Double.parseDouble(confidence.textValue());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ELEMENT_CONFIDENCE:index=" + elementIndex);
        }
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException("ELEMENT_CONFIDENCE:index=" + elementIndex);
        }
        return value;
    }
}
