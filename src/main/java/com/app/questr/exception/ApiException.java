package com.app.questr.exception;

import org.springframework.http.HttpStatus;

/** Base runtime exception. Subclasses carry the HTTP status code. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

