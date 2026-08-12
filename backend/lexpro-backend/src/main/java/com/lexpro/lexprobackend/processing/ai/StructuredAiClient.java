package com.lexpro.lexprobackend.processing.ai;

public interface StructuredAiClient {

    StructuredAiOutput generate(String systemPrompt, String userContent, String requestId);
}
