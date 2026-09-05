package com.lexpro.lexprobackend.processing.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiCompatibleLegalElementRecognitionClientTests {

    @Test
    void shouldGenerateUtf16EvidenceOffsetsWhenProviderReturnsOnlyQuote() throws Exception {
        String source = "\u539f\u544a\u5f20\u4e09\u4e8e2026\u5e748\u670812\u65e5\u63d0\u4ea4\u6750\u6599\u3002";
        ObjectMapper mapper = new ObjectMapper();
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        when(structuredClient.generate(anyString(), eq("Case-cause hint: not provided\n\nDocument:\n" + source),
                eq("job-legal-1"))).thenReturn(new StructuredAiOutput(mapper.readTree("""
                {"caseCause":null,"elements":[{"code":" fact ","name":"事实","content":"原告提交材料",
                "satisfied":true,"confidence":"0.95","evidence":[{"quote":" 张三 "}]}],
                "validation":{"warnings":[],"unsupportedClaims":[]}}
                """), "deepseek-v4-flash", mapper.createObjectNode(), null));

        OpenAiCompatibleLegalElementRecognitionClient client =
                new OpenAiCompatibleLegalElementRecognitionClient(structuredClient, new LegalElementJsonValidator());

        LegalElementRecognitionOutput output = client.recognize(source, null, "job-legal-1");

        assertEquals(2, output.rawElements().at("/elements/0/evidence/0/startOffset").intValue());
        assertEquals(4, output.rawElements().at("/elements/0/evidence/0/endOffset").intValue());
        assertEquals("FACT", output.rawElements().at("/elements/0/code").textValue());
        assertEquals("张三", output.rawElements().at("/elements/0/evidence/0/quote").textValue());
        assertEquals(0.95, output.rawElements().at("/elements/0/confidence").doubleValue());
        assertEquals("legal-elements-v3", output.schemaVersion());
    }

    @Test
    void shouldRejectEvidenceQuoteThatIsNotInSource() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        StructuredAiClient structuredClient = mock(StructuredAiClient.class);
        when(structuredClient.generate(anyString(), anyString(), eq("job-legal-2"))).thenReturn(
                new StructuredAiOutput(mapper.readTree("""
                        {"elements":[{"code":"FACT","name":"事实","content":"分析",
                        "evidence":[{"quote":"不存在的证据"}]}]}
                        """), "deepseek-v4-flash", mapper.createObjectNode(), null));

        OpenAiCompatibleLegalElementRecognitionClient client =
                new OpenAiCompatibleLegalElementRecognitionClient(structuredClient, new LegalElementJsonValidator());

        AiClientException exception = assertThrows(AiClientException.class,
                () -> client.recognize("原文没有这段话", null, "job-legal-2"));

        assertEquals("AI_RESPONSE_INVALID", exception.getErrorCode());
    }
}
