package com.unqueryservice.controller;

import com.unqueryservice.exception.DataSourceNotFoundException;
import com.unqueryservice.exception.PermissionDeniedException;
import com.unqueryservice.exception.QueryServiceException;
import com.unqueryservice.exception.SqlSecurityException;
import com.unqueryservice.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handling: maps every exception type to a standardised
 * {@link ErrorResponse} body so clients always get a consistent JSON error shape.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SqlSecurityException.class)
    public ResponseEntity<ErrorResponse> handleSqlSecurity(SqlSecurityException ex,
                                                            HttpServletRequest request) {
        log.warn("SQL security violation: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePermissionDenied(PermissionDeniedException ex,
                                                                  HttpServletRequest request) {
        log.warn("Permission denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DataSourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDataSourceNotFound(DataSourceNotFoundException ex,
                                                                    HttpServletRequest request) {
        log.warn("Data source not found: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(QueryServiceException.class)
    public ResponseEntity<ErrorResponse> handleQueryService(QueryServiceException ex,
                                                             HttpServletRequest request) {
        log.error("Query service error: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex,
                                                               HttpServletRequest request) {
        log.warn("Bad credentials for request: {}", request.getRequestURI());
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error: {}", msg);
        return build(HttpStatus.BAD_REQUEST, msg, request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex,
                                                        HttpServletRequest request) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request.getRequestURI());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String path) {
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
