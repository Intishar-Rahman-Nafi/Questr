package com.app.questr.exception;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String,String> fe = new LinkedHashMap<>();
        for (FieldError f : ex.getBindingResult().getFieldErrors()) fe.put(f.getField(), f.getDefaultMessage());
        return ResponseEntity.badRequest().body(ApiErrorResponse.of(400,"Bad Request","Validation failed",req.getRequestURI(),fe));
    }
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleEmail(EmailAlreadyExistsException ex, HttpServletRequest req) { return s(HttpStatus.CONFLICT, ex.getMessage(), req); }
    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiErrorResponse> handleUser(UsernameAlreadyExistsException ex, HttpServletRequest req) { return s(HttpStatus.CONFLICT, ex.getMessage(), req); }
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) { return s(HttpStatus.NOT_FOUND, ex.getMessage(), req); }
    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiErrorResponse> handleBadCreds(AuthenticationException ex, HttpServletRequest req) { return s(HttpStatus.UNAUTHORIZED, "Invalid username/email or password", req); }
    @ExceptionHandler(TokenException.class)
    public ResponseEntity<ApiErrorResponse> handleToken(TokenException ex, HttpServletRequest req) { return s(HttpStatus.UNAUTHORIZED, ex.getMessage(), req); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccess(AccessDeniedException ex, HttpServletRequest req) { return s(HttpStatus.FORBIDDEN, "Access denied", req); }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest req) { log.warn("ApiException: {}", ex.getMessage()); return s(ex.getStatus(), ex.getMessage(), req); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) { log.error("Unhandled: {}", ex.getMessage(), ex); return s(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req); }
    private ResponseEntity<ApiErrorResponse> s(HttpStatus hs, String msg, HttpServletRequest req) {
        return ResponseEntity.status(hs).body(ApiErrorResponse.of(hs.value(), hs.getReasonPhrase(), msg, req.getRequestURI()));
    }
}
