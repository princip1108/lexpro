package com.lexpro.lexprobackend.processing.parser;

import com.lexpro.lexprobackend.dossier.storage.DossierStorage;
import com.lexpro.lexprobackend.processing.ai.AiServiceDocumentClient;
import com.lexpro.lexprobackend.processing.ai.AiServiceParsedDocument;
import com.lexpro.lexprobackend.processing.config.AiServiceProperties;
import com.lexpro.lexprobackend.processing.config.DocumentProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinerUDocumentParserTests {

    @Test
    void shouldStayUnavailableUntilInternalAiServiceIsEnabled() {
        AiServiceProperties aiProperties = new AiServiceProperties();
        MinerUDocumentParser parser = new MinerUDocumentParser(mock(DossierStorage.class),
                mock(AiServiceDocumentClient.class), aiProperties, new DocumentProcessingProperties());

        assertFalse(parser.supports(job("source.pdf", "application/pdf")));

        aiProperties.setEnabled(true);
        assertTrue(parser.supports(job("source.pdf", "application/pdf")));
        assertTrue(parser.supports(job("source.DOCX", "application/octet-stream")));
        assertFalse(parser.supports(job("source.txt", "text/plain")));
        assertFalse(parser.supports(job("source.xlsx", "application/vnd.ms-excel")));
    }

    @Test
    void shouldReadPrivateStorageAndReturnValidatedMineruResult() throws Exception {
        DossierStorage storage = mock(DossierStorage.class);
        AiServiceDocumentClient client = mock(AiServiceDocumentClient.class);
        byte[] content = "not-a-real-pdf".getBytes(StandardCharsets.UTF_8);
        when(storage.get("cases/9/private.pdf"))
                .thenReturn(new DossierStorage.StoredResource(new ByteArrayResource(content), content.length));
        when(client.parse("source.pdf", "application/pdf", content, "request-123"))
                .thenReturn(new AiServiceParsedDocument("张某在北京", "{\"schemaVersion\":\"lexpro.parse.v2\"}",
                        "mineru-2.7.1/MinerU2.5-2509-1.2B"));
        AiServiceProperties aiProperties = enabledProperties();
        MinerUDocumentParser parser = new MinerUDocumentParser(storage, client, aiProperties,
                new DocumentProcessingProperties());

        ParsedDocument parsed = parser.parse(job("source.pdf", "application/pdf"));

        assertEquals("张某在北京", parsed.rawText());
        assertEquals("mineru-2.7.1/MinerU2.5-2509-1.2B", parsed.parserVersion());
        verify(client).parse("source.pdf", "application/pdf", content, "request-123");
    }

    @Test
    void shouldUseExtensionContentTypeInsteadOfUntrustedOctetStream() throws Exception {
        DossierStorage storage = mock(DossierStorage.class);
        AiServiceDocumentClient client = mock(AiServiceDocumentClient.class);
        byte[] content = {1, 2, 3};
        when(storage.get("cases/9/private.pdf"))
                .thenReturn(new DossierStorage.StoredResource(new ByteArrayResource(content), content.length));
        when(client.parse("source.pdf", "application/pdf", content, "request-123"))
                .thenReturn(new AiServiceParsedDocument("正文", "{}", "mineru/test"));
        MinerUDocumentParser parser = new MinerUDocumentParser(storage, client, enabledProperties(),
                new DocumentProcessingProperties());

        parser.parse(job("source.pdf", "application/octet-stream"));

        verify(client).parse("source.pdf", "application/pdf", content, "request-123");
    }

    @Test
    void shouldRejectStoredContentAboveConfiguredLimitBeforeCallingService() throws Exception {
        DossierStorage storage = mock(DossierStorage.class);
        when(storage.get("cases/9/private.pdf"))
                .thenReturn(new DossierStorage.StoredResource(new ByteArrayResource(new byte[] {1}), 3));
        AiServiceProperties aiProperties = enabledProperties();
        aiProperties.setMaxFileSize(DataSize.ofBytes(2));
        MinerUDocumentParser parser = new MinerUDocumentParser(storage, mock(AiServiceDocumentClient.class),
                aiProperties, new DocumentProcessingProperties());

        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> parser.parse(job("source.pdf", "application/pdf")));

        assertEquals("PARSER_FILE_TOO_LARGE", exception.getErrorCode());
    }

    private AiServiceProperties enabledProperties() {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setEnabled(true);
        properties.setInternalToken("12345678901234567890123456789012");
        return properties;
    }

    private DocumentParseJob job(String fileName, String contentType) {
        return new DocumentParseJob(12L, 5L, 9L, fileName, contentType,
                "cases/9/private.pdf", "ACTIVE", "{}", 7L, "request-123", 1);
    }
}

