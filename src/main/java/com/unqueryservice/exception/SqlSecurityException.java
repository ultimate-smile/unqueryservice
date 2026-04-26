package com.unqueryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a SQL statement violates the security sandbox rules.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class SqlSecurityException extends QueryServiceException {

    public SqlSecurityException(String message) {
        super(message);
    }
}
