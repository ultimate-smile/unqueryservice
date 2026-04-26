package com.unqueryservice.controller;

import com.unqueryservice.exception.DataSourceNotFoundException;
import com.unqueryservice.exception.QueryServiceException;
import com.unqueryservice.exception.SqlSecurityException;
import com.unqueryservice.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handler.
 *
 * <p>Every error is returned as an {@link ApiResponse} with a non-200 code so clients
 * always deal with the same envelope structure, success or failure:
 * <pre>
 * {
 *   "code":    403,
 *   "message": "Only SELECT statements are permitted.",
 *   "data":    null
 * }
 * </pre>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SqlSecurityException.class)
    public ResponseEntity<ApiResponse<Void>> handleSqlSecurity(SqlSecurityException ex,
                                                                 HttpServletRequest req) {
        log.warn("SQL security violation [{}]: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, ex.getMessage()));
    }

    @ExceptionHandler(DataSourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataSourceNotFound(DataSourceNotFoundException ex,
                                                                        HttpServletRequest req) {
        log.warn("Data source not found [{}]: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(QueryServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleQueryService(QueryServiceException ex,
                                                                  HttpServletRequest req) {
        log.error("Query service error [{}]: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex,
                                                               HttpServletRequest req) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation error [{}]: {}", req.getRequestURI(), msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(400, msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex,
                                                             HttpServletRequest req) {
        log.error("Unexpected error [{}]: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "An unexpected error occurred"));
    }
}
