package com.unqueryservice.controller;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.model.ApiResponse;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.service.CacheService;
import com.unqueryservice.service.QueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * REST endpoint for submitting queries and managing cache.
 *
 * <p>Every response is wrapped in {@link ApiResponse} to provide a uniform envelope:
 * <pre>
 * {
 *   "code":    200,
 *   "message": "success",
 *   "data":    { ... }
 * }
 * </pre>
 *
 * <p>When {@code page} and {@code pageSize} are both present in the request body,
 * the query runs in <b>paged mode</b> and {@code data} is a {@link com.unqueryservice.model.PageResult}.
 * Otherwise {@code data} is a {@link com.unqueryservice.model.QueryResult}.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;
    private final CacheService cacheService;
    private final DataSourceRegistry dataSourceRegistry;

    /**
     * POST /api/query
     *
     * <p><b>Non-paged request</b> (omit page/pageSize):
     * <pre>
     * {
     *   "dataSource": "mysql-snpi",
     *   "sql": "SELECT id, name FROM scene WHERE category = ?",
     *   "parameters": ["seawater"],
     *   "limit": 100
     * }
     * </pre>
     *
     * <p><b>Paged request</b> (include page + pageSize):
     * <pre>
     * {
     *   "dataSource": "mysql-snpi",
     *   "sql": "SELECT id, name FROM scene WHERE category = ?",
     *   "parameters": ["seawater"],
     *   "page": 1,
     *   "pageSize": 10
     * }
     * </pre>
     */
    @PostMapping("/query")
    public ResponseEntity<ApiResponse<?>> query(@Valid @RequestBody QueryRequest request) {
        boolean paged = request.getPage() != null && request.getPageSize() != null;
        if (paged) {
            return ResponseEntity.ok(ApiResponse.ok(queryService.executePaged(request)));
        }
        return ResponseEntity.ok(ApiResponse.ok(queryService.execute(request)));
    }

    /**
     * GET /api/datasources
     *
     * <p>Returns the list of registered data source names.
     */
    @GetMapping("/datasources")
    public ResponseEntity<ApiResponse<Set<String>>> listDataSources() {
        return ResponseEntity.ok(ApiResponse.ok(dataSourceRegistry.names()));
    }

    /**
     * DELETE /api/cache/{dataSource}
     *
     * <p>Evicts all cached query results for the given data source.
     */
    @DeleteMapping("/cache/{dataSource}")
    public ResponseEntity<ApiResponse<Void>> evictCache(@PathVariable String dataSource) {
        cacheService.evictDataSource(dataSource);
        log.info("Cache evicted for data source '{}'", dataSource);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
