package com.lexpro.lexprobackend.recommendation.client;

public class RetrievalClientException extends RuntimeException {

    private final String errorCode;

    public RetrievalClientException(String errorCode, Throwable cause) {
        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
