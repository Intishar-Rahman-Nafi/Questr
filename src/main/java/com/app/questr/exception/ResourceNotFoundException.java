package com.app.questr.exception;
import org.springframework.http.HttpStatus;
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found with id: " + id, HttpStatus.NOT_FOUND);
    }
}