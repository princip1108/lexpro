package com.lexpro.lexprobackend.processing.ai;

public interface LegalElementRecognitionClient {

    LegalElementRecognitionOutput recognize(String text, String caseCause, String requestId);
}
