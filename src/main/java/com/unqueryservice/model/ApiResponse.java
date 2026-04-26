package com.unqueryservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

/**
 * Unified HTTP response wrapper for all endpoints.
 *
 * <pre>
 * {
 *   "code":    200,
 *   "message": "success",
 *   "data":    { ... }   // null on error
 * }
 * </pre>
 *
 * @param <T> the type of the payload
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;

    private ApiResponse(int code, String message, T data) {
        this.code    = code;
        this.message = message;
        this.data    = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(200, "success", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
