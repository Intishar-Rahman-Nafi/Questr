package com.app.questr.exception;
import org.springframework.http.HttpStatus;
public class EmailAlreadyExistsException extends ApiException {
    public EmailAlreadyExistsException(String email) {
        super("Email already registered: " + email, HttpStatus.CONFLICT);
    }
}

