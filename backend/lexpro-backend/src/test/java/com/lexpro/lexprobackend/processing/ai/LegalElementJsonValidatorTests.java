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
}
