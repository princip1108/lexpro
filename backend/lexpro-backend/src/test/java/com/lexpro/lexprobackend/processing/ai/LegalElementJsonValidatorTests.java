package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegalElementJsonValidatorTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LegalElementJsonValidator validator = new LegalElementJsonValidator();

    @Test
    void shouldAcceptElementWithTraceableEvidence() throws Exception {
        JsonNode value = objectMapper.readTree("""
                {"elements":[{"code":"FACT","name":"Transfer","content":"A transfer occurred",
                "confidence":0.9,"evidence":[{"quote":"transferred 100 yuan","startOffset":3,"endOffset":23}]}],
                "validation":{"warnings":[]}}
                """);

        assertDoesNotThrow(() -> validator.validate(value, "Li transferred 100 yuan."));
    }

    @Test
    void shouldRejectEvidenceThatIsNotInSource() throws Exception {
        JsonNode value = objectMapper.readTree("""
                {"elements":[{"code":"FACT","name":"Transfer","content":"A transfer occurred",
                "evidence":[{"quote":"unsupported quote"}]}]}
                """);

        assertThrows(IllegalArgumentException.class, () -> validator.validate(value, "source text"));
    }

    @Test
    void shouldPreserveTypedAmountsAndRejectBooleanCoercion() throws Exception {
        var value = objectMapper.readTree("""
                {"elements":[{"code":"FACT","name":"涉案金额","value":12345.67,"content":"涉案金额12345.67元",
                "evidence":[{"quote":"12345.67元","startOffset":4,"endOffset":13}]}]}
                """);
        assertDoesNotThrow(() -> validator.validate(value, "😀𠮷12345.67元"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)value.path("elements").get(0)).put("value", true);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(value, "😀𠮷12345.67元"));
    }
}
