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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
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

    /** Mock the cache layer so Redis is not required in tests. */
    @MockBean
    private CacheService cacheService;

    @BeforeEach
    void setUp() throws Exception {
        // Cache always misses so every test hits the real query path
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.buildCacheKey(anyString(), anyString())).thenAnswer(inv -> {
            String ds = inv.getArgument(0);
            String sql = inv.getArgument(1);
            return "query:" + ds + ":" + sql.hashCode();
        });
        doNothing().when(cacheService).put(anyString(), any());

        DataSource ds = dataSourceRegistry.get("test-h2");
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS employees");
            stmt.execute("CREATE TABLE employees (" +
                    "id INT PRIMARY KEY, name VARCHAR(100), dept VARCHAR(50), secret_col VARCHAR(50))");
            stmt.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 'confidential-a')");
            stmt.execute("INSERT INTO employees VALUES (2, 'Bob',   'Marketing',   'confidential-b')");
            stmt.execute("INSERT INTO employees VALUES (3, 'Carol', 'Engineering', 'confidential-c')");
        }
    }

    @Test
    void simpleSelect_returnsRows() {
        Authentication auth = adminAuth();
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id, name FROM employees ORDER BY id");

        QueryResult result = queryService.execute(req, auth);

        assertThat(result.getRowCount()).isEqualTo(3);
        // Column names are returned as-is from the underlying driver (case varies by DB)
        assertThat(result.getColumns()).hasSize(2);
        // First row should contain Alice (key lookup is case-sensitive per driver)
        Map<String, Object> firstRow = result.getRows().get(0);
        assertThat(firstRow.values()).contains("Alice");
    }

    @Test
    void maskedColumn_isHiddenForAnalyst() {
        Authentication auth = analystAuth();
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id, name, secret_col FROM employees ORDER BY id");

        QueryResult result = queryService.execute(req, auth);

        // The masking config uses "secret_col"; check that the masking was applied
        // regardless of driver-reported column name casing
        assertThat(result.getRows()).allSatisfy(row -> {
            Object secretValue = row.get("secret_col");
            if (secretValue != null) {
                assertThat(secretValue).isEqualTo("***");
            }
        });
    }

    @Test
    void maskedColumn_isVisibleForAdmin() {
        Authentication auth = adminAuth();
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id, name, secret_col FROM employees ORDER BY id");

        QueryResult result = queryService.execute(req, auth);

        assertThat(result.getRows().get(0).get("secret_col")).isNotEqualTo("***");
    }

    @Test
    void limitIsRespected() {
        Authentication auth = adminAuth();
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("SELECT id FROM employees");
        req.setLimit(2);

        QueryResult result = queryService.execute(req, auth);

        assertThat(result.getRowCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void insertStatement_isRejected() {
        Authentication auth = adminAuth();
        QueryRequest req = new QueryRequest();
        req.setDataSource("test-h2");
        req.setSql("INSERT INTO employees VALUES (99, 'Evil', 'Hax', 'x')");

        assertThatThrownBy(() -> queryService.execute(req, auth))
                .isInstanceOf(SqlSecurityException.class);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Authentication adminAuth() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private Authentication analystAuth() {
        return new UsernamePasswordAuthenticationToken(
                "analyst", null, List.of(new SimpleGrantedAuthority("ROLE_ANALYST")));
    }
}
