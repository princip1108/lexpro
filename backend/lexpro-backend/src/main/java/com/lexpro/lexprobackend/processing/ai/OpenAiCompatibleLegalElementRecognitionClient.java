package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OpenAiCompatibleLegalElementRecognitionClient implements LegalElementRecognitionClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLegalElementRecognitionClient.class);
    static final String PROMPT_VERSION = "legal-elements-v4";
    static final String SCHEMA_VERSION = "legal-elements-v3";
    static final String SYSTEM_PROMPT = """
            Analyze legal elements only from the supplied document. Do not infer unsupported facts.
            Return one JSON object and no Markdown with this shape:
            {"caseCause":null,"courtName":null,"elements":[{"code":"FACT","name":"自首","value":true,"content":"brief analysis in Chinese","satisfied":true,"confidence":0.0,"evidence":[{"quote":"exact source text"}]}],"validation":{"warnings":[],"unsupportedClaims":[]}}
            Use specific Chinese legal-element names, not generic subject/object headings.
            Boolean value names include 自首、坦白、累犯、立功、认罪认罚、主犯、从犯、犯罪既遂、犯罪未遂、犯罪中止、缓刑、谅解、悔罪、前科劣迹、初犯偶犯、未成年、多次盗窃、入室盗窃、扒窃、电信网络诈骗、合同诈骗、持械抢劫、入户抢劫、致人重伤、致人死亡、防卫过当.
            Numeric value names include 涉案金额、盗窃数额、诈骗数额、抢劫数额、罚金、退赔金额、赔偿金额、毒品数量、刑期月数.
            Every element must include value: a JSON boolean, number, string or null. Never convert an amount to a boolean.
            Monetary values are in yuan, sentence duration in months; specify the unit in content for other quantities.
            Return only elements supported by the document. Absence of mention is NOT false; omit unsupported elements.
            Unknown values are null. Other case-specific element names may use concise string values.
            code must be SUBJECT, SUBJECTIVE, OBJECT, OBJECTIVE, FACT, EVIDENCE, LEGAL_BASIS, DISPUTE_FOCUS or OTHER.
            Every element must cite at least one exact quote from the source. IMPORTANT: copy each quote character-for-character
            from the supplied document, including exact Chinese wording and punctuation. Never paraphrase, normalize, combine,
            or invent a quote. Before returning JSON, verify that the source text contains each quote as one contiguous substring;
            if uncertain, use a shorter exact substring from the source. Do not return startOffset or endOffset;
            the server calculates UTF-16 offsets from each quote. Keep content and validation arrays concise.
            Confidence must be between 0 and 1. Use null for unknown satisfied state. Never invent legal citations.
            Do not include reasoning, explanations, Markdown, or fields outside this JSON object.
            Return at most 12 elements and at most 2 evidence quotes per element. Each quote must be a concise,
            verbatim source span of at most 120 characters so the complete JSON object fits within the response limit.
            """;

    private final StructuredAiClient client;
    private final LegalElementJsonValidator validator;

    public OpenAiCompatibleLegalElementRecognitionClient(StructuredAiClient client,
                                                         LegalElementJsonValidator validator) {
        this.client = client;
        this.validator = validator;
    }

    @Override
    public LegalElementRecognitionOutput recognize(String text, String caseCause, String requestId) {
        String hint = caseCause == null || caseCause.isBlank() ? "not provided" : caseCause;
        StructuredAiOutput output = client.generate(SYSTEM_PROMPT,
                "Case-cause hint: " + hint + "\n\nDocument:\n" + text, requestId);
        int generatedOffsets = normalizeEvidenceOffsets(output.content(), text);
        if (generatedOffsets > 0) {
            log.info("Generated legal-element evidence offsets requestId={} evidenceCount={}", requestId,
                    generatedOffsets);
        }
        try {
            validator.validate(output.content(), text);
        } catch (IllegalArgumentException exception) {
            String diagnosticCode = exception.getMessage() == null
                    ? "LEGAL_ELEMENT_SCHEMA" : exception.getMessage();
            throw new AiClientException("AI_RESPONSE_INVALID", diagnosticCode,
                    "AI legal-element response failed validation", exception);
        }
        JsonNode validation = output.content().get("validation");
        String detectedCause = output.content().path("caseCause").isTextual()
                ? output.content().path("caseCause").textValue() : caseCause;
        if (detectedCause != null && detectedCause.length() > 255) {
            throw new AiClientException("AI_RESPONSE_INVALID", "AI case cause exceeds the database limit");
        }
        return new LegalElementRecognitionOutput(output.content(),
                validation == null ? JsonNodeFactory.instance.objectNode() : validation,
                detectedCause, output.responseModel(), PROMPT_VERSION, SCHEMA_VERSION, SYSTEM_PROMPT,
                output.generationParameters(), output.tokenUsage());
    }

    /**
     * Provider output only carries exact quotes. Locate each quote in the source and
     * write Java UTF-16 offsets before the shared validator checks the result.
     */
    private int normalizeEvidenceOffsets(JsonNode root, String sourceText) {
        if (root == null || !root.isObject() || !root.path("elements").isArray() || sourceText == null) {
            return 0;
        }
        Map<String, Integer> nextSearchByQuote = new HashMap<>();
        int generated = 0;
        for (JsonNode element : root.path("elements")) {
            JsonNode evidence = element.path("evidence");
            if (!evidence.isArray()) {
                continue;
            }
            for (JsonNode item : evidence) {
                if (!(item instanceof ObjectNode object) || !item.path("quote").isTextual()) {
                    continue;
                }
                String quote = object.path("quote").textValue();
                if (quote == null || quote.isBlank()) {
                    continue;
                }
                quote = quote.trim();
                object.put("quote", quote);
                int searchFrom = nextSearchByQuote.getOrDefault(quote, 0);
                int start = sourceText.indexOf(quote, Math.min(searchFrom, sourceText.length()));
                if (start < 0) {
                    start = sourceText.indexOf(quote);
                }
                if (start < 0) {
                    continue;
                }
                object.put("startOffset", start);
                object.put("endOffset", start + quote.length());
                nextSearchByQuote.put(quote, start + quote.length());
                generated++;
            }
        }
        return generated;
    }
}
