package com.unqueryservice.service;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;
import com.unqueryservice.security.SqlSecuritySandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the full query pipeline:
 * <pre>
 *   Permission check → Security validation → Cache lookup
 *     → Calcite execution → Field masking → Cache store → Response
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    private final SqlSecuritySandbox sandbox;
    private final PermissionService permissionService;
    private final CacheService cacheService;
    private final CalciteQueryService calciteQueryService;
    private final QueryServiceProperties properties;

    @Override
    public QueryResult execute(QueryRequest request, Authentication authentication) {
        long startMs = System.currentTimeMillis();

        String dataSourceName = request.getDataSource();
        String sql = buildSql(request, dataSourceName);
        int maxRows = resolveMaxRows(request);

        // 1. Verify the user is allowed to access the requested data source
        permissionService.checkDataSourceAccess(dataSourceName, authentication);

        // 2. Validate the SQL through the security sandbox
        sandbox.validate(sql);

        // 3. Check the cache
        String cacheKey = cacheService.buildCacheKey(dataSourceName, sql);
        Optional<QueryResult> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            QueryResult hit = cached.get();
            hit.setCached(true);
            return hit;
        }

        // 4. Execute the query via Calcite
        log.info("Executing query on '{}': {}", dataSourceName,
                sql.length() > 200 ? sql.substring(0, 200) + "…" : sql);

        List<Map<String, Object>> rawRows = calciteQueryService.execute(
                dataSourceName, sql, request.getParameters(), maxRows);

        // 5. Apply field-level masking
        List<Map<String, Object>> rows = permissionService.applyFieldMasking(
                dataSourceName, authentication, rawRows);

        // 6. Derive column list from the first row (preserves order)
        List<String> columns = rows.isEmpty()
                ? List.of()
                : new ArrayList<>(rows.get(0).keySet());

        long elapsedMs = System.currentTimeMillis() - startMs;

        QueryResult result = QueryResult.builder()
                .dataSource(dataSourceName)
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .elapsedMs(elapsedMs)
                .cached(false)
                .build();

        // 7. Store in cache
        cacheService.put(cacheKey, result);

        log.info("Query on '{}' completed: {} rows in {}ms", dataSourceName, rows.size(), elapsedMs);
        return result;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Appends the row-level filter clause (if any) to the client-provided SQL.
     *
     * <p>The approach wraps the original query in a sub-select:
     * {@code SELECT * FROM (<originalSql>) _q WHERE <rowFilter>}
     * This is safe even when the original SQL already contains a WHERE clause.
     */
    private String buildSql(QueryRequest request, String dataSourceName) {
        String baseSql = request.getSql().strip();
        String rowFilter = permissionService.getRowFilter(dataSourceName);

        if (rowFilter.isBlank()) {
            return baseSql;
        }

        return "SELECT * FROM (" + baseSql + ") _q WHERE " + rowFilter;
    }

    private int resolveMaxRows(QueryRequest request) {
        int serverMax = properties.getMaxRowLimit();
        if (request.getLimit() == null || request.getLimit() <= 0) {
            return properties.getDefaultRowLimit();
        }
        return Math.min(request.getLimit(), serverMax);
    }
}
