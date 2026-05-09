package com.app.questr.exception;
import org.springframework.http.HttpStatus;
public class UsernameAlreadyExistsException extends ApiException {
    public UsernameAlreadyExistsException(String username) {
        super("Username already taken: " + username, HttpStatus.CONFLICT);
    }
}