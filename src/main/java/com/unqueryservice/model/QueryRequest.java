package com.unqueryservice.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Incoming query payload from clients.
 */
@Data
public class QueryRequest {

    /**
     * Target data source identifier (must match a key defined in configuration).
     */
    @NotBlank(message = "dataSource must not be blank")
    private String dataSource;

    /**
     * The SQL statement to execute.
     */
    @NotBlank(message = "sql must not be blank")
    @Size(max = 10_000, message = "sql must not exceed 10000 characters")
    private String sql;

    /**
     * Positional JDBC bind parameters (optional, used to prevent SQL injection).
     */
    private List<Object> parameters;

    /**
     * Maximum number of rows to return (server-side cap is enforced separately).
     */
    private Integer limit;

    /**
     * Hint string forwarded to Calcite's planner hints mechanism.
     */
    private String hint;
}
