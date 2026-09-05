package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.StructuredAiClient;
import com.lexpro.lexprobackend.processing.ai.StructuredAiOutput;
import com.lexpro.lexprobackend.report.domain.CaseCardSourceMaterial;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

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

    @Test
    void shouldBoundLongSourcesAndPreferDerivedResults() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"fields":[{"fieldCode":"CAUSE","fieldName":"案由","value":"诈骗罪",
                "sourceType":"SUMMARY","sourceId":21,"sourceText":"诈骗罪",
                "sourceLocation":{},"confidence":0.90}]}
                """);
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(content, "LexPro_8B",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleCaseCardGenerationClient(structuredClient);
        List<CaseCardSourceMaterial> sources = new ArrayList<>();
        sources.add(new CaseCardSourceMaterial("DOCUMENT", 12L, 8L, "文".repeat(15_000), true));
        sources.add(new CaseCardSourceMaterial("SUMMARY", 21L, null, "诈骗罪" + "摘".repeat(1_000), true));

        client.generate(sources, "request-long");

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(structuredClient).generate(anyString(), input.capture(), eq("request-long"));
        org.junit.jupiter.api.Assertions.assertTrue(input.getValue().length() < 1_900);
        org.junit.jupiter.api.Assertions.assertTrue(input.getValue().indexOf("[SUMMARY 21]")
                < input.getValue().indexOf("[DOCUMENT 12]"));
    }

    @Test
    void shouldNormalizeHyphenatedFieldCodeFromLexPro() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"fields":[{"fieldCode":"CO-CONSPIRATOR","fieldName":"共同犯罪嫌疑人","value":"张某",
                "sourceType":"SUMMARY","sourceId":21,"sourceText":"张某",
                "sourceLocation":{},"confidence":0.90}]}
                """);
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(content, "LexPro_8B",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleCaseCardGenerationClient(structuredClient);

        var output = client.generate(List.of(new CaseCardSourceMaterial(
                "SUMMARY", 21L, null, "共同犯罪嫌疑人为张某", true)), "request-code");

        assertEquals("CO_CONSPIRATOR", output.fields().getFirst().fieldCode());
    }

    @Test
    void shouldCorrectWrongSourceTypeWhenSourceIdIsUnique() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode content = (ObjectNode) objectMapper.readTree("""
                {"fields":[{"fieldCode":"INVESTIGATOR_NAME","fieldName":"侦查人员","value":"王某",
                "sourceType":"DOCUMENT","sourceId":25,"sourceText":"侦查人员王某",
                "sourceLocation":{},"confidence":0.90}]}
                """);
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(content, "LexPro_8B",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleCaseCardGenerationClient(structuredClient);

        var output = client.generate(List.of(new CaseCardSourceMaterial(
                "SUMMARY", 25L, null, "侦查人员王某依法办理", true)), "request-source");

        assertEquals("SUMMARY", output.fields().getFirst().sourceType());
        assertEquals(25L, output.fields().getFirst().sourceId());
    }
}
