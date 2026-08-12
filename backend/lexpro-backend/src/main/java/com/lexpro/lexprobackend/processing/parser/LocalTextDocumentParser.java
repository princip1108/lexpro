package com.lexpro.lexprobackend.processing.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lexpro.lexprobackend.dossier.storage.DossierStorage;
import com.lexpro.lexprobackend.processing.config.DocumentProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class LocalTextDocumentParser implements DocumentParser {

    private static final String PARSER_VERSION = "local-text/1";
    private final DossierStorage storage;
    private final DocumentProcessingProperties properties;
    private final ObjectMapper objectMapper;

    public LocalTextDocumentParser(DossierStorage storage, DocumentProcessingProperties properties,
                                   ObjectMapper objectMapper) {
        this.storage = storage;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(DocumentParseJob job) {
        String fileType = job.fileType() == null ? "" : job.fileType().toLowerCase(Locale.ROOT);
        String fileName = job.fileName() == null ? "" : job.fileName().toLowerCase(Locale.ROOT);
        return fileType.equals("text/plain") || fileName.endsWith(".txt");
    }

    @Override
    public ParsedDocument parse(DocumentParseJob job) {
        if (!"ACTIVE".equals(job.fileStatus())) {
            throw new DocumentProcessingException("DOSSIER_FILE_DELETED",
                    "The source dossier file is no longer active");
        }
        try {
            String text = readUtf8(job.fileUrl());
            ObjectNode structured = objectMapper.createObjectNode();
            structured.put("schemaVersion", "lexpro.parse.v1");
            structured.put("contentType", "text/plain");
            structured.put("textLength", text.length());
            return new ParsedDocument(text, objectMapper.writeValueAsString(structured), PARSER_VERSION);
        } catch (JsonProcessingException exception) {
            throw new DocumentProcessingException("PARSED_RESULT_SERIALIZATION_FAILED",
                    "The parsed result could not be serialized", exception);
        } catch (IOException exception) {
            throw new DocumentProcessingException("DOSSIER_CONTENT_UNAVAILABLE",
                    "The source dossier content could not be read", exception);
        }
    }

    private String readUtf8(String objectKey) throws IOException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        StringBuilder text = new StringBuilder();
        try (Reader reader = new InputStreamReader(storage.get(objectKey).resource().getInputStream(), decoder)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                if (text.length() + read > properties.getMaxExtractedChars()) {
                    throw new DocumentProcessingException("PARSED_TEXT_TOO_LARGE",
                            "The extracted text exceeds the configured character limit");
                }
                text.append(buffer, 0, read);
            }
        }
        return text.toString();
    }
}
