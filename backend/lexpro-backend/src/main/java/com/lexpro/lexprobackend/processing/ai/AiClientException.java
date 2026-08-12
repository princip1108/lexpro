package com.lexpro.lexprobackend.processing.ai;

public class AiClientException extends RuntimeException {

    private final String errorCode;
    private final String diagnosticCode;

    public AiClientException(String errorCode, String message) {
        this(errorCode, errorCode, message);
    }

    public AiClientException(String errorCode, String message, Throwable cause) {
        this(errorCode, errorCode, message, cause);
    }

    public AiClientException(String errorCode, String diagnosticCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.diagnosticCode = diagnosticCode;
    }

    public AiClientException(String errorCode, String diagnosticCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.diagnosticCode = diagnosticCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }
}
