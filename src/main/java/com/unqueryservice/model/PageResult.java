package com.unqueryservice.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Paginated query result, returned when {@code page} and {@code pageSize}
 * are present in the request.
 */
@Data
@Builder
public class PageResult {

    /** Column names in order. */
    private List<String> columns;

    /** Rows for the current page. */
    private List<Map<String, Object>> rows;

    /** Total number of rows matching the query (without paging). */
    private long total;

    /** Current page number (1-based). */
    private int page;

    /** Page size used for this request. */
    private int pageSize;

    /** Total number of pages. */
    private int totalPages;

    /** Whether this result was served from the cache. */
    private boolean cached;

    /** Name of the data source. */
    private String dataSource;

    /** Elapsed time in milliseconds for the full operation. */
    private long elapsedMs;
}
