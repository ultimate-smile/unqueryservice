package com.unqueryservice.service;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.exception.SqlSecurityException;
import com.unqueryservice.model.PageResult;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration test for the full query pipeline using an in-memory H2 database.
 *
 * <p>{@link CacheService} is mocked so no running Redis instance is required.
 */
@SpringBootTest
@ActiveProfiles("test")
class QueryServiceIntegrationTest {

    @Autowired
    private QueryService queryService;

    @Autowired
    private DataSourceRegistry dataSourceRegistry;

    @MockBean
    private CacheService cacheService;

    @BeforeEach
    void setUp() throws Exception {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.buildCacheKey(anyString(), anyString())).thenAnswer(inv ->
                "query:" + inv.getArgument(0) + ":" + inv.getArgument(1).hashCode());
        doNothing().when(cacheService).put(anyString(), any());

        DataSource ds = dataSourceRegistry.get("test-h2");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS employees");
            stmt.execute("CREATE TABLE employees (" +
                    "id INT PRIMARY KEY, name VARCHAR(100), dept VARCHAR(50))");
            for (int i = 1; i <= 25; i++) {
                stmt.execute("INSERT INTO employees VALUES (" + i + ", 'Employee" + i + "', " +
                        (i % 2 == 0 ? "'Engineering'" : "'Marketing'") + ")");
            }
        }
    }

    // ------------------------------------------------------------------
    // Non-paged tests
    // ------------------------------------------------------------------

    @Test
    void simpleSelect_returnsAllRows() {
        QueryResult result = queryService.execute(req("SELECT id, name FROM employees ORDER BY id"));
        assertThat(result.getRowCount()).isEqualTo(25);
        assertThat(result.getColumns()).hasSize(2);
        assertThat(result.getRows().get(0).values()).contains("Employee1");
    }

    @Test
    void selectWithWhereClause_filtersRows() {
        QueryResult result = queryService.execute(req("SELECT id FROM employees WHERE dept = 'Engineering'"));
        assertThat(result.getRowCount()).isEqualTo(12);
    }

    @Test
    void limitIsRespected() {
        QueryRequest r = req("SELECT id FROM employees");
        r.setLimit(5);
        assertThat(queryService.execute(r).getRowCount()).isLessThanOrEqualTo(5);
    }

    @Test
    void cacheHit_returnsCachedResult() {
        QueryResult cached = QueryResult.builder()
                .dataSource("test-h2").columns(java.util.List.of("id"))
                .rows(java.util.List.of()).rowCount(0).elapsedMs(1).cached(false).build();
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        QueryResult result = queryService.execute(req("SELECT id FROM employees"));
        assertThat(result.isCached()).isTrue();
    }

    @Test
    void insertStatement_isRejected() {
        assertThatThrownBy(() -> queryService.execute(
                req("INSERT INTO employees VALUES (99, 'Evil', 'Hax')")))
                .isInstanceOf(SqlSecurityException.class);
    }

    @Test
    void elapsedTime_isPopulated() {
        assertThat(queryService.execute(req("SELECT id FROM employees LIMIT 1")).getElapsedMs())
                .isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Paged tests
    // ------------------------------------------------------------------

    @Test
    void pagedQuery_firstPage_returnsCorrectSlice() {
        PageResult result = queryService.executePaged(pagedReq("SELECT id FROM employees ORDER BY id", 1, 10));

        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getRows()).hasSize(10);
    }

    @Test
    void pagedQuery_lastPage_returnsRemainingRows() {
        PageResult result = queryService.executePaged(pagedReq("SELECT id FROM employees ORDER BY id", 3, 10));

        assertThat(result.getTotal()).isEqualTo(25);
        assertThat(result.getRows()).hasSize(5);   // 25 - 20 = 5 on the last page
        assertThat(result.getPage()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(3);
    }

    @Test
    void pagedQuery_withFilter_countReflectsFilter() {
        PageResult result = queryService.executePaged(
                pagedReq("SELECT id, name FROM employees WHERE dept = 'Engineering' ORDER BY id", 1, 5));

        assertThat(result.getTotal()).isEqualTo(12);
        assertThat(result.getTotalPages()).isEqualTo(3);  // ceil(12/5)
        assertThat(result.getRows()).hasSize(5);
    }

    @Test
    void pagedQuery_metadataIsCorrect() {
        PageResult result = queryService.executePaged(pagedReq("SELECT id FROM employees", 1, 10));

        assertThat(result.getDataSource()).isEqualTo("test-h2");
        assertThat(result.isCached()).isFalse();
        assertThat(result.getElapsedMs()).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private QueryRequest req(String sql) {
        QueryRequest r = new QueryRequest();
        r.setDataSource("test-h2");
        r.setSql(sql);
        return r;
    }

    private QueryRequest pagedReq(String sql, int page, int pageSize) {
        QueryRequest r = req(sql);
        r.setPage(page);
        r.setPageSize(pageSize);
        return r;
    }
}
