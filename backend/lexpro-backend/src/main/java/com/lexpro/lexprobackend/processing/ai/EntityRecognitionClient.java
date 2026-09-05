package com.lexpro.lexprobackend.processing.ai;

public interface EntityRecognitionClient {

    EntityRecognitionOutput recognize(String text, String requestId);

    default EntityRecognitionOutput recognize(String text, String parsedTextJson, String requestId) {
        return recognize(text, requestId);
    }
}
