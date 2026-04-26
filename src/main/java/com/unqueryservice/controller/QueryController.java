package com.unqueryservice.controller;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;
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
 * <p>Authentication and permission control are handled by ThingsBoard upstream.
 * This service trusts all inbound requests.
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
     * <p>Executes a SQL query against the specified data source and returns
     * the result set (or a cached copy if the same query was recently executed).
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResult> query(@Valid @RequestBody QueryRequest request) {
        QueryResult result = queryService.execute(request);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/datasources
     *
     * <p>Returns the list of registered data source names.
     */
    @GetMapping("/datasources")
    public ResponseEntity<Set<String>> listDataSources() {
        return ResponseEntity.ok(dataSourceRegistry.names());
    }

    /**
     * DELETE /api/cache/{dataSource}
     *
     * <p>Evicts all cached query results for the given data source.
     */
    @DeleteMapping("/cache/{dataSource}")
    public ResponseEntity<Void> evictCache(@PathVariable String dataSource) {
        cacheService.evictDataSource(dataSource);
        log.info("Cache evicted for data source '{}'", dataSource);
        return ResponseEntity.noContent().build();
    }
}
