package com.lexpro.lexprobackend.processing.parser;

public record ParsedDocument(String rawText, String parsedTextJson, String parserVersion) {
}
