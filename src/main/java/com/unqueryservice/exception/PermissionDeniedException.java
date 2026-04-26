package com.unqueryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the authenticated user lacks permission for the requested operation.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PermissionDeniedException extends QueryServiceException {

    public PermissionDeniedException(String message) {
        super(message);
    }
}
