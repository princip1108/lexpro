package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.config.AiProcessingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleEntityRecognitionClientTests {

    @Test
    void shouldCallCompatibleEndpointAndValidateStructuredResponse() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProcessingProperties properties = properties();
        properties.setEnableThinking(true);
        String providerResponse = new ObjectMapper().writeValueAsString(Map.of(
                "model", "deepseek-v4-flash",
                "choices", List.of(Map.of("message", Map.of("content",
                        "{\"entities\":[{\"type\":\"SUSPECT\",\"text\":\"Zhang\",\"confidence\":0.9}]}"))),
                "usage", Map.of("prompt_tokens", 12, "completion_tokens", 8, "total_tokens", 20)));
        OpenAiCompatibleStructuredAiClient client = new OpenAiCompatibleStructuredAiClient(
                builder.build(), properties, new ObjectMapper());
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("X-Request-Id", "job-123"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.chat_template_kwargs.enable_thinking").value(true))
                .andRespond(withSuccess(providerResponse, MediaType.APPLICATION_JSON));

        StructuredAiOutput output = client.generate("Extract entities", "Zhang submitted the filing.", "job-123");

        assertEquals("Zhang", output.content().at("/entities/0/text").textValue());
        assertEquals("deepseek-v4-flash", output.responseModel());
        assertEquals(20, output.tokenUsage().path("total_tokens").intValue());
        server.verify();
    }

    @Test
    void shouldRejectContentThatDoesNotMatchEntitySchema() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(structuredClient.generate(anyString(), eq("source"), eq("job-123"))).thenReturn(
                new StructuredAiOutput(objectMapper.readTree(
                        "{\"entities\":[{\"type\":\"SUSPECT\",\"text\":\"hallucinated\"}]}"),
                        "deepseek-v4-flash", objectMapper.createObjectNode(), null));
        OpenAiCompatibleEntityRecognitionClient client = new OpenAiCompatibleEntityRecognitionClient(
                structuredClient);

        AiClientException exception = assertThrows(AiClientException.class,
                () -> client.recognize("source", "job-123"));

        assertEquals("AI_RESPONSE_INVALID", exception.getErrorCode());
        assertEquals("ENTITY_NOT_IN_SOURCE", exception.getDiagnosticCode());
    }

    @Test
    void shouldClassifyOutputTruncatedByProvider() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProcessingProperties properties = properties();
        String providerResponse = new ObjectMapper().writeValueAsString(Map.of(
                "model", "deepseek-v4-flash",
                "choices", List.of(Map.of(
                        "finish_reason", "length",
                        "message", Map.of("content", "{\"entities\":[{"))),
                "usage", Map.of("completion_tokens", 4096)));
        OpenAiCompatibleStructuredAiClient client = new OpenAiCompatibleStructuredAiClient(
                builder.build(), properties, new ObjectMapper());
        server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess(providerResponse, MediaType.APPLICATION_JSON));

        AiClientException exception = assertThrows(AiClientException.class,
                () -> client.generate("Extract entities", "source", "job-truncated"));

        assertEquals("AI_RESPONSE_INVALID", exception.getErrorCode());
        assertEquals("OUTPUT_TRUNCATED", exception.getDiagnosticCode());
        server.verify();
    }

    @Test
    void shouldNormalizeHarmlessEntityFormattingDifferences() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(structuredClient.generate(anyString(), eq("原告张三提交材料。"), eq("job-normalize")))
                .thenReturn(new StructuredAiOutput(objectMapper.readTree("""
                        {"entities":[{"type":" suspect ","text":" 张三 ","confidence":"0.95"}]}
                        """), "deepseek-chat", objectMapper.createObjectNode(), null));
        OpenAiCompatibleEntityRecognitionClient client = new OpenAiCompatibleEntityRecognitionClient(
                structuredClient);

        EntityRecognitionOutput output = client.recognize("原告张三提交材料。", "job-normalize");

        assertEquals("SUSPECT", output.entities().at("/entities/0/type").textValue());
        assertEquals("张三", output.entities().at("/entities/0/text").textValue());
        assertEquals(0.95, output.entities().at("/entities/0/confidence").doubleValue());
        assertEquals(2, output.entities().at("/entities/0/startOffset").intValue());
    }

    @Test
    void shouldRecalibrateIncorrectOffsetsForTraceableChineseEntity() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(structuredClient.generate(anyString(), eq("原告张三向法院提交材料，张三请求还款。"), eq("job-123")))
                .thenReturn(new StructuredAiOutput(objectMapper.readTree("""
                        {"entities":[{"type":"SUSPECT","text":"张三","startOffset":99,"endOffset":101,
                        "confidence":0.95}]}
                        """), "deepseek-chat", objectMapper.createObjectNode(), null));
        OpenAiCompatibleEntityRecognitionClient client = new OpenAiCompatibleEntityRecognitionClient(
                structuredClient);

        EntityRecognitionOutput output = client.recognize("原告张三向法院提交材料，张三请求还款。", "job-123");

        assertEquals(2, output.entities().at("/entities/0/startOffset").intValue());
        assertEquals(4, output.entities().at("/entities/0/endOffset").intValue());
    }

    @Test
    void shouldGenerateOffsetsWhenProviderOmitsThem() throws Exception {
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        when(structuredClient.generate(anyString(), eq("张三提交材料，李四签收。"), eq("job-123")))
                .thenReturn(new StructuredAiOutput(objectMapper.readTree("""
                        {"entities":[
                          {"type":"SUSPECT","text":"张三","confidence":0.98},
                          {"type":"SUSPECT","text":"李四","confidence":0.97}
                        ]}
                        """), "deepseek-chat", objectMapper.createObjectNode(), null));
        OpenAiCompatibleEntityRecognitionClient client = new OpenAiCompatibleEntityRecognitionClient(
                structuredClient);

        EntityRecognitionOutput output = client.recognize("张三提交材料，李四签收。", "job-123");

        assertEquals(0, output.entities().at("/entities/0/startOffset").intValue());
        assertEquals(2, output.entities().at("/entities/0/endOffset").intValue());
        assertEquals(7, output.entities().at("/entities/1/startOffset").intValue());
        assertEquals(9, output.entities().at("/entities/1/endOffset").intValue());
    }

    private AiProcessingProperties properties() {
        AiProcessingProperties properties = new AiProcessingProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("https://api.deepseek.com"));
        properties.setApiKey("test-key");
        properties.setModel("deepseek-v4-flash");
        return properties;
    }
}
