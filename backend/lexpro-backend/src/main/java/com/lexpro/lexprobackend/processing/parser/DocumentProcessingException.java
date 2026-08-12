package com.lexpro.lexprobackend.processing.parser;

public class DocumentProcessingException extends RuntimeException {

    private final String errorCode;

    public DocumentProcessingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DocumentProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
