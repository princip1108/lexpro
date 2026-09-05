package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.processing.parser.DocumentProcessingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class HttpAiServiceDocumentClientTests {

    @Test
    void shouldAcceptWordProvenanceAndPreserveSafeFailureCodes() {
        String text = "张𠮷\n\n北京";
        var client = new HttpAiServiceDocumentClient(RestClient.create(), new ObjectMapper());
        assertEquals(text, client.validateResponse(response(text, sha256(text), 5, 7)
                .replace("\"parser\":\"mineru\"", "\"parser\":\"docx\"")
                .getBytes(StandardCharsets.UTF_8)).text());
        for (var status : new HttpStatus[]{HttpStatus.UNPROCESSABLE_ENTITY, HttpStatus.SERVICE_UNAVAILABLE}) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:8020");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://127.0.0.1:8020/internal/v1/documents/parse?fileName=source.docx"))
                    .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                            .body(status.is4xxClientError() ? "{\"detail\":\"WORD_DOCUMENT_INVALID\"}"
                                    : "{\"detail\":\"private provider body\"}"));
            var httpClient = new HttpAiServiceDocumentClient(builder.build(), new ObjectMapper());
            var error = assertThrows(DocumentProcessingException.class,
                    () -> httpClient.parse("source.docx", "application/octet-stream", new byte[]{1}, "test"));
            assertEquals(status.is4xxClientError() ? "WORD_DOCUMENT_INVALID" : "MINERU_SERVICE_UNAVAILABLE", error.getErrorCode());
            assertEquals("Document parsing failed", error.getMessage());
            server.verify();
        }
    }

    @Test
    void shouldSendRawDocumentWithInternalHeadersAndValidateUtf16Contract() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://127.0.0.1:8020")
                .defaultHeader("X-LexPro-Internal-Token", "test-internal-token");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        byte[] source = {1, 2, 3};
        String text = "张𠮷\n\n北京";
        String response = response(text, sha256(text), 5, 7);
        server.expect(requestTo("http://127.0.0.1:8020/internal/v1/documents/parse?fileName=source.pdf"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-LexPro-Internal-Token", "test-internal-token"))
                .andExpect(header("X-Request-ID", "request-123"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(source))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        HttpAiServiceDocumentClient client = new HttpAiServiceDocumentClient(builder.build(), new ObjectMapper());

        AiServiceParsedDocument parsed = client.parse("source.pdf", "application/pdf", source, "request-123");

        assertEquals(text, parsed.text());
        assertEquals("mineru-test/1", parsed.parserVersion());
        server.verify();
    }

    @Test
    void shouldRejectResponseWhenTextHashDoesNotMatch() {
        HttpAiServiceDocumentClient client = new HttpAiServiceDocumentClient(RestClient.create(), new ObjectMapper());

        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> client.validateResponse(response("张𠮷\n\n北京", "0".repeat(64), 5, 7)
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("MINERU_RESPONSE_INVALID", exception.getErrorCode());
    }

    @Test
    void shouldRejectResponseWhenUtf16OffsetsUseCodePointUnits() {
        String text = "张𠮷\n\n北京";
        HttpAiServiceDocumentClient client = new HttpAiServiceDocumentClient(RestClient.create(), new ObjectMapper());

        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> client.validateResponse(response(text, sha256(text), 4, 6)
                        .getBytes(StandardCharsets.UTF_8)));

        assertEquals("MINERU_RESPONSE_INVALID", exception.getErrorCode());
    }

    @Test
    void shouldRejectResponseWhenBlockOrderIsNotCanonical() {
        String text = "张𠮷\n\n北京";
        String response = response(text, sha256(text), 5, 7).replace("\"order\":1", "\"order\":3");
        HttpAiServiceDocumentClient client = new HttpAiServiceDocumentClient(RestClient.create(), new ObjectMapper());

        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> client.validateResponse(response.getBytes(StandardCharsets.UTF_8)));

        assertEquals("MINERU_RESPONSE_INVALID", exception.getErrorCode());
    }

    private String response(String text, String hash, int secondStart, int secondEnd) {
        return """
                {
                  "schemaVersion":"lexpro.parse.v2",
                  "offsetUnit":"UTF16_CODE_UNIT",
                  "parser":"mineru",
                  "parserVersion":"mineru-test/1",
                  "text":"张𠮷\\n\\n北京",
                  "textSha256":"%s",
                  "blocks":[
                    {"blockId":"b1","text":"张𠮷","order":0,"globalStartUtf16":0,"globalEndUtf16":3},
                    {"blockId":"b2","text":"北京","order":1,"globalStartUtf16":%d,"globalEndUtf16":%d}
                  ]
                }
                """.formatted(hash, secondStart, secondEnd);
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
