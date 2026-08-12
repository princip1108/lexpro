package com.lexpro.lexprobackend.processing.parser;

import com.lexpro.lexprobackend.processing.domain.DocumentParseJob;

public interface DocumentParser {

    boolean supports(DocumentParseJob job);

    ParsedDocument parse(DocumentParseJob job);
}
