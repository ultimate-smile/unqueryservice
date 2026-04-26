package com.unqueryservice.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Standardised error payload returned on all error HTTP responses.
 */
@Data
@Builder
public class ErrorResponse {

    private int status;
    private String error;
    private String message;
    private String path;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
