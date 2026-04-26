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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * REST endpoint for submitting queries and managing cache.
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
     * <p>Executes a SQL query against the specified data source. Returns the
     * result set (or a cached copy if the same query was recently executed).
     *
     * <p>Requires a valid {@code Authorization: Bearer <token>} header.
     */
    @PostMapping("/query")
    public ResponseEntity<QueryResult> query(@Valid @RequestBody QueryRequest request,
                                             Authentication authentication) {
        QueryResult result = queryService.execute(request, authentication);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/datasources
     *
     * <p>Returns the list of registered data source names. Useful for clients
     * to discover available targets without reading server configuration.
     */
    @GetMapping("/datasources")
    public ResponseEntity<Set<String>> listDataSources() {
        return ResponseEntity.ok(dataSourceRegistry.names());
    }

    /**
     * DELETE /api/cache/{dataSource}
     *
     * <p>Evicts all cached query results for the given data source.
     * Restricted to users with the {@code ROLE_ADMIN} authority.
     */
    @DeleteMapping("/cache/{dataSource}")
    public ResponseEntity<Void> evictCache(@PathVariable String dataSource,
                                           Authentication authentication) {
        // Check admin role
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equalsIgnoreCase("ROLE_ADMIN")
                            || a.getAuthority().equalsIgnoreCase("ADMIN"));
        if (!isAdmin) {
            return ResponseEntity.status(403).build();
        }

        cacheService.evictDataSource(dataSource);
        log.info("Cache evicted for data source '{}' by '{}'", dataSource, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
