package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.StructuredAiClient;
import com.lexpro.lexprobackend.processing.ai.StructuredAiOutput;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleCaseCardGenerationClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldAcceptTraceableField() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"fields":[{"fieldCode":"CASE_AMOUNT","fieldName":"涉案金额","value":"48万元",
                "sourceType":"DOCUMENT","sourceId":12,"sourceText":"转账48万元",
                "sourceLocation":{"page":2},"confidence":0.96}]}
                """);
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(content, "deepseek-v4-flash",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleCaseCardGenerationClient(structuredClient);

        var output = client.generate(List.of(new CaseCardSourceMaterial(
                "DOCUMENT", 12L, 8L, "被害人累计转账48万元。", true)), "request-1");

        assertEquals("CASE_AMOUNT", output.fields().getFirst().fieldCode());
        assertEquals(12L, output.fields().getFirst().sourceId());
    }

    @Test
    void shouldRejectQuoteAbsentFromSelectedSource() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"fields":[{"fieldCode":"CASE_AMOUNT","fieldName":"涉案金额","value":"100万元",
                "sourceType":"DOCUMENT","sourceId":12,"sourceText":"转账100万元",
                "sourceLocation":{},"confidence":0.90}]}
                """);
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(content, "deepseek-v4-flash",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleCaseCardGenerationClient(structuredClient);

        AiClientException exception = assertThrows(AiClientException.class, () -> client.generate(
                List.of(new CaseCardSourceMaterial("DOCUMENT", 12L, 8L, "转账48万元", true)), "request-1"));

        assertEquals("AI_RESPONSE_INVALID", exception.getErrorCode());
    }
}
