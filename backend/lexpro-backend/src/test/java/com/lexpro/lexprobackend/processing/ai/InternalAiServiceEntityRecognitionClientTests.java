package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InternalAiServiceEntityRecognitionClientTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSendMineruBlocksAndAcceptExactUtf16Locations() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://127.0.0.1:8020")
                .defaultHeader("X-LexPro-Internal-Token", "test-internal-token");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String text = "张𠮷\n\n北京";
        server.expect(requestTo("http://127.0.0.1:8020/internal/v1/entities/recognize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-LexPro-Internal-Token", "test-internal-token"))
                .andExpect(header("X-Request-ID", "job-1"))
                .andExpect(jsonPath("$.blocks[1].blockId").value("b2"))
                .andRespond(withSuccess(response(text, 5, 7), MediaType.APPLICATION_JSON));
        InternalAiServiceEntityRecognitionClient client =
                new InternalAiServiceEntityRecognitionClient(builder.build(), objectMapper);

        EntityRecognitionOutput output = client.recognize(text, parseContract(text), "job-1");

        assertEquals("LexPro_8B", output.modelName());
        assertEquals("lexpro.entity.v2", output.schemaVersion());
        assertEquals(5, output.entities().at("/entities/0/startOffset").intValue());
        server.verify();
    }

    @Test
    void shouldUseOneCanonicalBlockForLocalTextParsing() {
        InternalAiServiceEntityRecognitionClient client =
                new InternalAiServiceEntityRecognitionClient(RestClient.create(), objectMapper);

        ObjectNode request = client.buildRequest("本地文本", "{\"schemaVersion\":\"lexpro.parse.v1\"}", "job-2");

        assertEquals("document-000000", request.at("/blocks/0/blockId").textValue());
        assertEquals("本地文本", request.at("/blocks/0/text").textValue());
    }

    @Test
    void shouldRejectCodePointOffsetsPresentedAsUtf16() throws Exception {
        String text = "张𠮷\n\n北京";
        InternalAiServiceEntityRecognitionClient client =
                new InternalAiServiceEntityRecognitionClient(RestClient.create(), objectMapper);
        ObjectNode request = client.buildRequest(text, parseContract(text), "job-3");

        AiClientException exception = assertThrows(AiClientException.class,
                () -> client.validateResponse(response(text, 4, 6).getBytes(StandardCharsets.UTF_8), request));

        assertEquals("AI_RESPONSE_INVALID", exception.getErrorCode());
        assertEquals("ENTITY_OFFSET_INVALID", exception.getDiagnosticCode());
    }

    private String parseContract(String text) {
        return """
                {"schemaVersion":"lexpro.parse.v2","offsetUnit":"UTF16_CODE_UNIT",
                 "text":"张𠮷\\n\\n北京","textSha256":"%s","blocks":[
                   {"blockId":"b1","text":"张𠮷","order":0},
                   {"blockId":"b2","text":"北京","order":1}
                 ]}
                """.formatted(sha256(text));
    }

    private String response(String text, int start, int end) {
        return """
                {"schemaVersion":"lexpro.entity.v2","offsetUnit":"UTF16_CODE_UNIT",
                 "modelName":"LexPro_8B","modelVersion":"server-test/1",
                 "sourceTextSha256":"%s","entities":[
                   {"type":"LOCATION","text":"北京","blockId":"b2",
                    "blockStartUtf16":0,"blockEndUtf16":2,
                    "globalStartUtf16":%d,"globalEndUtf16":%d,"occurrenceIndex":0}
                 ],"documentEntities":[]}
                """.formatted(sha256(text), start, end);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
