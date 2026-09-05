package com.lexpro.lexprobackend.processing.parser;

import com.lexpro.lexprobackend.dossier.storage.DossierStorage;
import com.lexpro.lexprobackend.processing.ai.AiServiceDocumentClient;
import com.lexpro.lexprobackend.processing.ai.AiServiceParsedDocument;
import com.lexpro.lexprobackend.processing.config.AiServiceProperties;
import com.lexpro.lexprobackend.processing.config.DocumentProcessingProperties;
import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class MinerUDocumentParser implements DocumentParser {

    private static final Set<String> EXTENSIONS = Set.of("pdf", "doc", "docx", "jpg", "jpeg", "png");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png"
    );
    private final DossierStorage storage;
    private final AiServiceDocumentClient client;
    private final AiServiceProperties aiServiceProperties;
    private final DocumentProcessingProperties processingProperties;

    public MinerUDocumentParser(DossierStorage storage, AiServiceDocumentClient client,
                                AiServiceProperties aiServiceProperties,
                                DocumentProcessingProperties processingProperties) {
        this.storage = storage;
        this.client = client;
        this.aiServiceProperties = aiServiceProperties;
        this.processingProperties = processingProperties;
    }

    @Override
    public boolean supports(DocumentParseJob job) {
        return aiServiceProperties.isEnabled() && EXTENSIONS.contains(extension(job.fileName()));
    }

    @Override
    public ParsedDocument parse(DocumentParseJob job) {
        if (!"ACTIVE".equals(job.fileStatus())) {
            throw new DocumentProcessingException("DOSSIER_FILE_DELETED",
                    "The source dossier file is no longer active");
        }
        String extension = extension(job.fileName());
        if (!EXTENSIONS.contains(extension)) {
            throw new DocumentProcessingException("PARSER_UNAVAILABLE_FOR_FILE_TYPE",
                    "MinerU does not support this dossier file type");
        }
        try {
            DossierStorage.StoredResource stored = storage.get(job.fileUrl());
            long maxBytes = aiServiceProperties.getMaxFileSize().toBytes();
            if (stored.size() > maxBytes) {
                throw new DocumentProcessingException("PARSER_FILE_TOO_LARGE",
                        "The source dossier file exceeds the parser limit");
            }
            byte[] content;
            try (InputStream input = stored.resource().getInputStream()) {
                content = input.readNBytes(Math.toIntExact(maxBytes + 1));
            }
            if (content.length > maxBytes) {
                throw new DocumentProcessingException("PARSER_FILE_TOO_LARGE",
                        "The source dossier file exceeds the parser limit");
            }
            AiServiceParsedDocument parsed = client.parse(job.fileName(), CONTENT_TYPES.get(extension),
                    content, job.requestId());
            if (parsed.text().length() > processingProperties.getMaxExtractedChars()) {
                throw new DocumentProcessingException("PARSED_TEXT_TOO_LARGE",
                        "The extracted text exceeds the configured character limit");
            }
            return new ParsedDocument(parsed.text(), parsed.payloadJson(), parsed.parserVersion());
        } catch (IOException exception) {
            throw new DocumentProcessingException("DOSSIER_CONTENT_UNAVAILABLE",
                    "The source dossier content could not be read", exception);
        }
    }

    private String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int separator = fileName.lastIndexOf('.');
        return separator < 0 ? "" : fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }
}

