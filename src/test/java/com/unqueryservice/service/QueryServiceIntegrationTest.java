package com.unqueryservice.service;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.exception.SqlSecurityException;
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
            stmt.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering')");
            stmt.execute("INSERT INTO employees VALUES (2, 'Bob',   'Marketing')");
            stmt.execute("INSERT INTO employees VALUES (3, 'Carol', 'Engineering')");
        }
    }

    @Test
    void simpleSelect_returnsAllRows() {
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id, name FROM employees ORDER BY id");

        QueryResult result = queryService.execute(req);

        assertThat(result.getRowCount()).isEqualTo(3);
        assertThat(result.getColumns()).hasSize(2);
        assertThat(result.getRows().get(0).values()).contains("Alice");
    }

    @Test
    void selectWithWhereClause_filtersRows() {
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id, name FROM employees WHERE dept = 'Engineering'");

        QueryResult result = queryService.execute(req);

        assertThat(result.getRowCount()).isEqualTo(2);
    }

    @Test
    void limitIsRespected() {
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id FROM employees");
        req.setLimit(2);

        QueryResult result = queryService.execute(req);

        assertThat(result.getRowCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void cacheHit_returnsMarkedResult() {
        QueryResult cached = QueryResult.builder()
                .dataSource("test-h2")
                .columns(java.util.List.of("id"))
                .rows(java.util.List.of())
                .rowCount(0)
                .elapsedMs(1)
                .cached(false)
                .build();
        when(cacheService.get(anyString())).thenReturn(Optional.of(cached));

        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id FROM employees");

        QueryResult result = queryService.execute(req);

        assertThat(result.isCached()).isTrue();
    }

    @Test
    void insertStatement_isRejected() {
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("INSERT INTO employees VALUES (99, 'Evil', 'Hax')");

        assertThatThrownBy(() -> queryService.execute(req))
                .isInstanceOf(SqlSecurityException.class);
    }

    @Test
    void elapsedTime_isPopulated() {
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id FROM employees");

        QueryResult result = queryService.execute(req);

        assertThat(result.getElapsedMs()).isGreaterThanOrEqualTo(0);
    }
}
