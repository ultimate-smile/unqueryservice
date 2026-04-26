package com.unqueryservice.service;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.model.PageResult;
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
 * Orchestrates two query pipelines:
 *
 * <p><b>Non-paged:</b>
 * <pre>SQL validation → cache → execute → cache write → QueryResult</pre>
 *
 * <p><b>Paged:</b>
 * <pre>SQL validation → cache → COUNT(*) → data slice (LIMIT/OFFSET) → cache write → PageResult</pre>
 *
 * <p>The paged path wraps the caller's SQL in a sub-select so that any ORDER BY,
 * WHERE, JOIN, etc. in the original query is fully preserved:
 * <pre>
 *   SELECT COUNT(*) FROM (<originalSql>) _count_wrap
 *   SELECT * FROM (<originalSql>) _page_wrap LIMIT ? OFFSET ?
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    private final SqlSecuritySandbox sandbox;
    private final CacheService cacheService;
    private final CalciteQueryService calciteQueryService;
    private final QueryServiceProperties properties;

    // -----------------------------------------------------------------------
    // Non-paged
    // -----------------------------------------------------------------------

    @Override
    public QueryResult execute(QueryRequest request) {
        long startMs = System.currentTimeMillis();
        String dataSourceName = request.getDataSource();
        String sql            = request.getSql().strip();
        int    maxRows        = resolveMaxRows(request);

        sandbox.validate(sql);

        String cacheKey = cacheService.buildCacheKey(dataSourceName, sql);
        Optional<QueryResult> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            QueryResult hit = cached.get();
            hit.setCached(true);
            return hit;
        }

        log.info("Executing query on '{}': {}", dataSourceName, abbreviate(sql));
        List<Map<String, Object>> rows = calciteQueryService.execute(
                dataSourceName, sql, request.getParameters(), maxRows);

        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

        QueryResult result = QueryResult.builder()
                .dataSource(dataSourceName)
                .columns(columns)
                .rows(rows)
                .rowCount(rows.size())
                .elapsedMs(System.currentTimeMillis() - startMs)
                .cached(false)
                .build();

        cacheService.put(cacheKey, result);
        log.info("Query on '{}' completed: {} rows in {}ms", dataSourceName, rows.size(), result.getElapsedMs());
        return result;
    }

    // -----------------------------------------------------------------------
    // Paged
    // -----------------------------------------------------------------------

    @Override
    public PageResult executePaged(QueryRequest request) {
        long startMs = System.currentTimeMillis();
        String dataSourceName = request.getDataSource();
        String sql            = request.getSql().strip();
        int    page           = request.getPage();
        int    pageSize       = request.getPageSize();
        int    offset         = (page - 1) * pageSize;

        sandbox.validate(sql);

        // Cache key includes page + pageSize so different pages are cached separately
        String cacheKey = cacheService.buildCacheKey(
                dataSourceName, sql + "|page=" + page + "|size=" + pageSize);
        Optional<QueryResult> cached = cacheService.get(cacheKey);
        if (cached.isPresent()) {
            QueryResult hit = cached.get();
            // Re-inflate as PageResult from cached QueryResult
            long total = hit.getRowCount();   // stored total in rowCount during first run
            return buildPageResult(hit.getColumns(), hit.getRows(), total,
                    page, pageSize, true, dataSourceName,
                    System.currentTimeMillis() - startMs);
        }

        // 1. COUNT query to get the total number of matching rows
        String countSql = "SELECT COUNT(*) AS _total FROM (" + sql + ") _count_wrap";
        log.info("Counting rows on '{}': {}", dataSourceName, abbreviate(sql));
        List<Map<String, Object>> countRows = calciteQueryService.execute(
                dataSourceName, countSql, request.getParameters(), 1);
        long total = extractCount(countRows);

        // 2. Paged data query
        String pagedSql = "SELECT * FROM (" + sql + ") _page_wrap LIMIT " + pageSize + " OFFSET " + offset;
        log.info("Paged query on '{}' (page={}, size={}): {}", dataSourceName, page, pageSize, abbreviate(sql));
        List<Map<String, Object>> rows = calciteQueryService.execute(
                dataSourceName, pagedSql, request.getParameters(), pageSize);

        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());

        long elapsedMs = System.currentTimeMillis() - startMs;
        PageResult result = buildPageResult(columns, rows, total, page, pageSize,
                false, dataSourceName, elapsedMs);

        // Cache the paged result by converting to QueryResult (reuse cache infrastructure)
        // Store total in rowCount so it survives the round-trip
        QueryResult cachePayload = QueryResult.builder()
                .dataSource(dataSourceName)
                .columns(columns)
                .rows(rows)
                .rowCount((int) total)   // total stored here intentionally
                .elapsedMs(elapsedMs)
                .cached(false)
                .build();
        cacheService.put(cacheKey, cachePayload);

        log.info("Paged query on '{}' completed: {}/{} rows in {}ms",
                dataSourceName, rows.size(), total, elapsedMs);
        return result;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private PageResult buildPageResult(List<String> columns, List<Map<String, Object>> rows,
                                        long total, int page, int pageSize,
                                        boolean cached, String dataSource, long elapsedMs) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return PageResult.builder()
                .columns(columns)
                .rows(rows)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .cached(cached)
                .dataSource(dataSource)
                .elapsedMs(elapsedMs)
                .build();
    }

    private long extractCount(List<Map<String, Object>> countRows) {
        if (countRows.isEmpty()) return 0L;
        Map<String, Object> row = countRows.get(0);
        // Column name may be _total or _TOTAL depending on the driver
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("_total") && entry.getValue() != null) {
                return ((Number) entry.getValue()).longValue();
            }
        }
        // Fallback: take the first value in the row
        Object first = row.values().iterator().next();
        return first == null ? 0L : ((Number) first).longValue();
    }

    private int resolveMaxRows(QueryRequest request) {
        int serverMax = properties.getMaxRowLimit();
        if (request.getLimit() == null || request.getLimit() <= 0) {
            return properties.getDefaultRowLimit();
        }
        return Math.min(request.getLimit(), serverMax);
    }

    private String abbreviate(String sql) {
        return sql.length() > 200 ? sql.substring(0, 200) + "…" : sql;
    }
}
