package com.unqueryservice.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Unified query result returned to clients.
 */
@Data
@Builder
public class QueryResult {

    /** Column names in the order returned by the query. */
    private List<String> columns;

    /**
     * Row data: each map uses the column name as key and the column value as value.
     * Values may be {@code null} for SQL NULL cells.
     */
    private List<Map<String, Object>> rows;

    /** Total number of rows in this response (after permission filtering). */
    private int rowCount;

    /** Time taken to execute and fetch the query, in milliseconds. */
    private long elapsedMs;

    /** Name of the data source that served the query. */
    private String dataSource;

    /** Whether the result was served from the cache. */
    private boolean cached;

    /** UTC timestamp when the result was produced. */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
