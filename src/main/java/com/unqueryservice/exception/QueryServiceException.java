package com.unqueryservice.exception;

/**
 * Base runtime exception for the query service.
 */
public class QueryServiceException extends RuntimeException {

    public QueryServiceException(String message) {
        super(message);
    }

    public QueryServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
