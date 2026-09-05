package com.lexpro.lexprobackend.processing.ai;

public interface AiServiceDocumentClient {

    AiServiceParsedDocument parse(String fileName, String contentType, byte[] content, String requestId);
}

