package com.lexpro.lexprobackend.processing.parser;

import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DocumentParserRegistry {

    private final List<DocumentParser> parsers;

    public DocumentParserRegistry(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public ParsedDocument parse(DocumentParseJob job) {
        return parsers.stream()
                .filter(parser -> parser.supports(job))
                .findFirst()
                .orElseThrow(() -> new DocumentProcessingException(
                        "PARSER_UNAVAILABLE_FOR_FILE_TYPE",
                        "No approved parser is configured for this file type"
                ))
                .parse(job);
    }
}
