package com.lexpro.lexprobackend.processing.web.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record StartDocumentParseRequest(
        @Size(max = 50) Map<String, Object> parserParameters
) {
}
