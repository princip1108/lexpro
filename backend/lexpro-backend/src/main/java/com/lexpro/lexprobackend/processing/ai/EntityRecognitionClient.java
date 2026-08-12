package com.lexpro.lexprobackend.processing.ai;

public interface EntityRecognitionClient {

    EntityRecognitionOutput recognize(String text, String requestId);
}
