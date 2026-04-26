package com.unqueryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the requested data source identifier is not configured.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DataSourceNotFoundException extends QueryServiceException {

    public DataSourceNotFoundException(String name) {
        super("Data source not found: " + name);
    }
}
