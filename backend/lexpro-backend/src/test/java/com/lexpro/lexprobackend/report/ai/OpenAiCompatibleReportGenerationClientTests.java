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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
