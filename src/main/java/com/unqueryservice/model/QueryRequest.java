package com.unqueryservice.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Incoming query payload from clients.
 *
 * <p>Pagination mode is activated when both {@code page} and {@code pageSize} are provided.
 * In this mode the service executes a COUNT query first, then the paged data query,
 * and returns a {@link PageResult} wrapped in {@link ApiResponse}.
 *
 * <p>When {@code page}/{@code pageSize} are absent the service executes the SQL as-is
 * and returns a {@link QueryResult} wrapped in {@link ApiResponse}.
 */
@Data
public class QueryRequest {

    /**
     * Target data source identifier (must match a key in application.yml).
     */
    @NotBlank(message = "dataSource must not be blank")
    private String dataSource;

    /**
     * The SQL SELECT statement to execute.
     */
    @NotBlank(message = "sql must not be blank")
    @Size(max = 10_000, message = "sql must not exceed 10000 characters")
    private String sql;

    /**
     * Positional JDBC bind parameters — each '?' marker in the SQL maps to one entry here.
     * Extra entries are ignored so clients can pass pagination values separately from
     * SQL that already contains literal LIMIT/OFFSET values.
     * Using parameters is strongly preferred over string-concatenating values into sql.
     */
    private List<Object> parameters;

    /**
     * Non-paged row limit (ignored when pagination is active).
     * Defaults to {@code query-service.default-row-limit} when not set.
     */
    private Integer limit;

    // -----------------------------------------------------------------------
    // Pagination (optional — both fields must be set to enable paged mode)
    // -----------------------------------------------------------------------

    /**
     * Page number, 1-based.
     */
    @Min(value = 1, message = "page must be >= 1")
    private Integer page;

    /**
     * Number of rows per page.
     */
    @Min(value = 1,   message = "pageSize must be >= 1")
    @Max(value = 1000, message = "pageSize must be <= 1000")
    private Integer pageSize;
}
