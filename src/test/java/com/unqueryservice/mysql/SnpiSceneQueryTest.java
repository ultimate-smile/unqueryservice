package com.unqueryservice.mysql;

import com.unqueryservice.exception.SqlSecurityException;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;
import com.unqueryservice.service.CacheService;
import com.unqueryservice.service.QueryService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Live MySQL integration tests against the {@code snpi} database, {@code scene} table.
 *
 * <p>Table DDL:
 * <pre>
 * CREATE TABLE `scene` (
 *   `id`          bigint NOT NULL AUTO_INCREMENT,
 *   `name`        varchar(64)  NOT NULL COMMENT '场景名称',
 *   `category`    enum('seawater','rubber','firefighting') NOT NULL COMMENT '场景分类',
 *   `dimension`   varchar(32)  NOT NULL COMMENT '尺寸',
 *   `plant`       varchar(32)  NOT NULL COMMENT '推送电厂',
 *   `access_code` varchar(255) DEFAULT NULL COMMENT '权限分配',
 *   `resource_id` bigint       DEFAULT NULL COMMENT '缩略图(资源ID)',
 *   `create_by`   bigint       DEFAULT NULL,
 *   `create_time` datetime     DEFAULT NULL,
 *   `update_by`   bigint       DEFAULT NULL,
 *   `update_time` datetime     DEFAULT NULL,
 *   PRIMARY KEY (`id`),
 *   KEY `idx_name` (`name`)
 * ) COMMENT='场景表';
 * </pre>
 *
 * <p>Tests are automatically <b>skipped</b> when MySQL is unreachable
 * (controlled by {@link MySqlAvailabilityExtension}).
 */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-it")
@ExtendWith(MySqlAvailabilityExtension.class)
@DisplayName("snpi.scene – live MySQL query tests")
class SnpiSceneQueryTest {

    private static final String DS = "mysql-snpi";

    @Autowired
    private QueryService queryService;

    /** Redis is not available in test environments; cache is disabled via profile. */
    @MockBean
    private CacheService cacheService;

    @BeforeEach
    void stubCache() {
        when(cacheService.get(anyString())).thenReturn(Optional.empty());
        when(cacheService.buildCacheKey(anyString(), anyString())).thenAnswer(inv ->
                "query:" + inv.getArgument(0) + ":" + inv.getArgument(1).hashCode());
        doNothing().when(cacheService).put(anyString(), any());
    }

    // -----------------------------------------------------------------------
    // Basic query
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SELECT * returns rows with all expected columns")
    void selectAll_containsExpectedColumns() {
        QueryResult result = execute("SELECT * FROM scene LIMIT 10");

        assertThat(result.getRowCount()).isGreaterThanOrEqualTo(0);
        if (!result.getRows().isEmpty()) {
            Set<String> cols = result.getRows().get(0).keySet();
            // Column names may be returned in any case by the driver
            assertThat(cols.stream().map(String::toLowerCase))
                    .contains("id", "name", "category", "dimension", "plant");
        }
    }

    @Test
    @DisplayName("SELECT specific columns returns only those columns")
    void selectSpecificColumns_onlyRequestedColumnsReturned() {
        QueryResult result = execute("SELECT id, name, category FROM scene LIMIT 5");

        assertThat(result.getColumns()).hasSize(3);
        assertThat(result.getColumns().stream().map(String::toLowerCase))
                .containsExactlyInAnyOrder("id", "name", "category");
    }

    // -----------------------------------------------------------------------
    // Filtering by category (ENUM column)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Filter by category='seawater' returns only seawater scenes")
    void filterByCategory_seawater_returnsMatchingRows() {
        QueryResult result = execute(
                "SELECT id, name, category FROM scene WHERE category = 'seawater' LIMIT 100");

        result.getRows().forEach(row -> {
            Object cat = row.get("category") != null ? row.get("category") : row.get("CATEGORY");
            assertThat(cat).asString().isEqualToIgnoringCase("seawater");
        });
    }

    @Test
    @DisplayName("Filter by category='rubber' returns only rubber scenes")
    void filterByCategory_rubber_returnsMatchingRows() {
        QueryResult result = execute(
                "SELECT id, name, category FROM scene WHERE category = 'rubber' LIMIT 100");

        result.getRows().forEach(row -> {
            Object cat = row.get("category") != null ? row.get("category") : row.get("CATEGORY");
            assertThat(cat).asString().isEqualToIgnoringCase("rubber");
        });
    }

    @Test
    @DisplayName("Filter by category='firefighting' returns only firefighting scenes")
    void filterByCategory_firefighting_returnsMatchingRows() {
        QueryResult result = execute(
                "SELECT id, name, category FROM scene WHERE category = 'firefighting' LIMIT 100");

        result.getRows().forEach(row -> {
            Object cat = row.get("category") != null ? row.get("category") : row.get("CATEGORY");
            assertThat(cat).asString().isEqualToIgnoringCase("firefighting");
        });
    }

    // -----------------------------------------------------------------------
    // Filtering and sorting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ORDER BY id ASC returns rows in ascending id order")
    void orderById_ascending() {
        QueryResult result = execute("SELECT id FROM scene ORDER BY id ASC LIMIT 20");

        List<Map<String, Object>> rows = result.getRows();
        for (int i = 1; i < rows.size(); i++) {
            long prev = toLong(colValue(rows.get(i - 1), "id"));
            long curr = toLong(colValue(rows.get(i), "id"));
            assertThat(curr).isGreaterThanOrEqualTo(prev);
        }
    }

    @Test
    @DisplayName("ORDER BY id DESC returns rows in descending id order")
    void orderById_descending() {
        QueryResult result = execute("SELECT id FROM scene ORDER BY id DESC LIMIT 20");

        List<Map<String, Object>> rows = result.getRows();
        for (int i = 1; i < rows.size(); i++) {
            long prev = toLong(colValue(rows.get(i - 1), "id"));
            long curr = toLong(colValue(rows.get(i), "id"));
            assertThat(curr).isLessThanOrEqualTo(prev);
        }
    }

    @Test
    @DisplayName("LIMIT clause caps the result set")
    void limitClause_capsResults() {
        QueryResult result = execute("SELECT id FROM scene LIMIT 3");

        assertThat(result.getRowCount()).isLessThanOrEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Aggregate queries
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("COUNT(*) returns a single row with a numeric count")
    void count_returnsNonNegativeNumber() {
        QueryResult result = execute("SELECT COUNT(*) AS total FROM scene");

        assertThat(result.getRowCount()).isEqualTo(1);
        Object total = colValue(result.getRows().get(0), "total");
        assertThat(toLong(total)).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("GROUP BY category returns one row per distinct category value")
    void groupByCategory_returnsDistinctCategories() {
        QueryResult result = execute(
                "SELECT category, COUNT(*) AS cnt FROM scene GROUP BY category ORDER BY category");

        // There are at most 3 possible ENUM values
        assertThat(result.getRowCount()).isLessThanOrEqualTo(3);
        result.getRows().forEach(row -> {
            Object cat = colValue(row, "category");
            assertThat(cat).asString()
                    .isIn("seawater", "rubber", "firefighting");
        });
    }

    // -----------------------------------------------------------------------
    // name index lookup
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Filter by name LIKE pattern returns matching rows")
    void filterByNameLike_returnsMatchingRows() {
        // Query is safe: % wildcard in a literal value, not injected into SQL structure
        QueryResult result = execute("SELECT id, name FROM scene WHERE name LIKE '%场景%' LIMIT 20");

        result.getRows().forEach(row -> {
            Object name = colValue(row, "name");
            assertThat(name).asString().contains("场景");
        });
    }

    // -----------------------------------------------------------------------
    // Security sandbox tests (DML must be rejected)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("INSERT into scene is rejected by the SQL sandbox")
    void insert_isRejectedBySandbox() {
        assertThatThrownBy(() -> execute(
                "INSERT INTO scene (name, category, dimension, plant) VALUES ('test', 'seawater', '100', 'plant1')"))
                .isInstanceOf(SqlSecurityException.class);
    }

    @Test
    @DisplayName("UPDATE scene is rejected by the SQL sandbox")
    void update_isRejectedBySandbox() {
        assertThatThrownBy(() -> execute("UPDATE scene SET plant = 'evil' WHERE id = 1"))
                .isInstanceOf(SqlSecurityException.class);
    }

    @Test
    @DisplayName("DELETE from scene is rejected by the SQL sandbox")
    void delete_isRejectedBySandbox() {
        assertThatThrownBy(() -> execute("DELETE FROM scene WHERE id = 1"))
                .isInstanceOf(SqlSecurityException.class);
    }

    // -----------------------------------------------------------------------
    // Result metadata
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Elapsed time is always populated")
    void elapsedTime_isPopulated() {
        QueryResult result = execute("SELECT id FROM scene LIMIT 1");
        assertThat(result.getElapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getDataSource()).isEqualTo(DS);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private QueryResult execute(String sql) {
        QueryRequest req = new QueryRequest();
        req.setDataSource(DS);
        req.setSql(sql);
        return queryService.execute(req);
    }

    /** Case-insensitive column lookup (drivers differ in capitalisation). */
    private Object colValue(Map<String, Object> row, String column) {
        if (row.containsKey(column)) return row.get(column);
        if (row.containsKey(column.toUpperCase())) return row.get(column.toUpperCase());
        // Search case-insensitively
        return row.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(column))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }
}
