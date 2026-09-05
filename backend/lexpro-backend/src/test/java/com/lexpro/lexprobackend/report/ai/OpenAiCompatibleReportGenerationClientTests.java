package com.lexpro.lexprobackend.report.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.processing.ai.AiClientException;
import com.lexpro.lexprobackend.processing.ai.StructuredAiClient;
import com.lexpro.lexprobackend.processing.ai.StructuredAiOutput;
import com.lexpro.lexprobackend.report.domain.ReportSourceMaterial;
import com.lexpro.lexprobackend.report.validation.ReportContentValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class OpenAiCompatibleReportGenerationClientTests {

    @Test
    void shouldRequireStructuredReportContent() {
        ObjectMapper objectMapper = new ObjectMapper();
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode output = objectMapper.createObjectNode().put("reportContent", "plain text");
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(output, "deepseek-v4-flash",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleReportGenerationClient(structuredClient, new ReportContentValidator());

        AiClientException exception = assertThrows(AiClientException.class, () -> client.generate(
                "REVIEW_REPORT", objectMapper.readTree("""
                        {"sections":[{"code":"FACTS","title":"案件事实","instructions":"概述事实"}]}
                        """),
                List.of(new ReportSourceMaterial("EVIDENCE", 12L, "document", "text", true)), "request-1"));

        assertEquals("AI_RESPONSE_INVALID", exception.getErrorCode());
    }

    @Test
    void shouldBoundLongReportSources() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectNode output = (ObjectNode) objectMapper.readTree("""
                {"reportContent":{"sections":[{"code":"FACTS","title":"案件事实","content":"待审查"}]}}
                """);
        when(structuredClient.generate(anyString(), anyString(), anyString()))
                .thenReturn(new StructuredAiOutput(output, "LexPro_8B",
                        objectMapper.createObjectNode(), null));
        var client = new OpenAiCompatibleReportGenerationClient(structuredClient, new ReportContentValidator());

        client.generate("REVIEW_REPORT", objectMapper.readTree("""
                        {"sections":[{"code":"FACTS","title":"案件事实","instructions":"概述事实"}]}
                        """),
                List.of(new ReportSourceMaterial("EVIDENCE", 12L, "document", "文".repeat(15_000), true)),
                "request-long");

        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(structuredClient).generate(anyString(), input.capture(), eq("request-long"));
        assertTrue(input.getValue().length() < 2_100);
    }
}
