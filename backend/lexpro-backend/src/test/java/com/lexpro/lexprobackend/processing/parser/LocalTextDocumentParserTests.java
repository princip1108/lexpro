package com.lexpro.lexprobackend.processing.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexpro.lexprobackend.dossier.storage.DossierStorage;
import com.lexpro.lexprobackend.processing.config.DocumentProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalTextDocumentParserTests {

    @Test
    void shouldParseUtf8TextWithoutExposingStorageKeyInResult() throws Exception {
        DossierStorage storage = mock(DossierStorage.class);
        byte[] content = "案件事实文本".getBytes(StandardCharsets.UTF_8);
        when(storage.get("cases/9/private.txt"))
                .thenReturn(new DossierStorage.StoredResource(new ByteArrayResource(content), content.length));
        LocalTextDocumentParser parser = new LocalTextDocumentParser(storage,
                new DocumentProcessingProperties(), new ObjectMapper());

        ParsedDocument parsed = parser.parse(job());

        assertEquals("案件事实文本", parsed.rawText());
        assertEquals("local-text/1", parsed.parserVersion());
        assertEquals("lexpro.parse.v1",
                new ObjectMapper().readTree(parsed.parsedTextJson()).get("schemaVersion").asText());
    }

    @Test
    void shouldEnforceExtractedCharacterLimit() throws Exception {
        DossierStorage storage = mock(DossierStorage.class);
        byte[] content = "123456".getBytes(StandardCharsets.UTF_8);
        when(storage.get("cases/9/private.txt"))
                .thenReturn(new DossierStorage.StoredResource(new ByteArrayResource(content), content.length));
        DocumentProcessingProperties properties = new DocumentProcessingProperties();
        properties.setMaxExtractedChars(5);
        LocalTextDocumentParser parser = new LocalTextDocumentParser(storage, properties, new ObjectMapper());

        DocumentProcessingException exception = assertThrows(DocumentProcessingException.class,
                () -> parser.parse(job()));

        assertEquals("PARSED_TEXT_TOO_LARGE", exception.getErrorCode());
    }

    private DocumentParseJob job() {
        return new DocumentParseJob(12L, 5L, 9L, "source.txt", "text/plain",
                "cases/9/private.txt", "ACTIVE", "{}", 7L, "request-123", 1);
    }
}
