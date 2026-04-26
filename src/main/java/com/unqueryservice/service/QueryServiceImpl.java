package com.unqueryservice.service;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;
import com.unqueryservice.util.SqlSecuritySandbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates the full query pipeline:
 * <pre>
 *   SQL validation → Cache lookup → Calcite execution → Cache store → Response
 * </pre>
 *
 * <p>Authentication and permission control are handled upstream by ThingsBoard.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    private final SqlSecuritySandbox sandbox;
    private final CacheService cacheService;
    private final CalciteQueryService calciteQueryService;
    private final QueryServiceProperties properties;

    @Override
    public QueryResult execute(QueryRequest request) {
        long startMs = System.currentTimeMillis();

        String dataSourceName = request.getDataSource();
        String sql = request.getSql().strip();
        int maxRows = resolveMaxRows(request);

        // 1. Validate the SQL through the security sandbox
        sandbox.validate(sql);

        // 2. Check the cache
        String cacheKey = cacheService.buildCacheKey(dataSourceName, sql);
        Optional<QueryResult> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            QueryResult hit = cached.get();
            hit.setCached(true);
            return hit;
        }

        // 3. Execute the query via Calcite
        log.info("Executing query on '{}': {}", dataSourceName,
                sql.length() > 200 ? sql.substring(0, 200) + "…" : sql);

        List<Map<String, Object>> rows = calciteQueryService.execute(
                dataSourceName, sql, request.getParameters(), maxRows);

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

        // 4. Store in cache
        cacheService.put(cacheKey, result);

        log.info("Query on '{}' completed: {} rows in {}ms", dataSourceName, rows.size(), elapsedMs);
        return result;
    }

    private int resolveMaxRows(QueryRequest request) {
        int serverMax = properties.getMaxRowLimit();
        if (request.getLimit() == null || request.getLimit() <= 0) {
            return properties.getDefaultRowLimit();
        }
        return Math.min(request.getLimit(), serverMax);
    }
}
