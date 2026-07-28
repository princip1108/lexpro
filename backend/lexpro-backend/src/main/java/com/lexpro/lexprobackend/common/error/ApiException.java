package com.lexpro.lexprobackend.common.error;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;
    private final String errorCode;

    public ApiException(HttpStatus status, String title, String errorCode, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
