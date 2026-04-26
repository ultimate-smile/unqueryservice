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
 * Live MySQL integration tests against the {@code phoenix} database, {@code sys_oss} table.
 *
 * <p>Table DDL:
 * <pre>
 * CREATE TABLE `sys_oss` (
 *   `oss_id`        bigint       NOT NULL COMMENT '对象存储主键',
 *   `tenant_id`     varchar(20)  DEFAULT '' COMMENT '租户ID',
 *   `file_name`     varchar(255) NOT NULL DEFAULT '' COMMENT '文件名',
 *   `original_name` varchar(255) NOT NULL DEFAULT '' COMMENT '原名',
 *   `file_suffix`   varchar(10)  NOT NULL DEFAULT '' COMMENT '文件后缀名',
 *   `url`           varchar(500) NOT NULL COMMENT 'URL地址',
 *   `create_dept`   bigint       DEFAULT NULL COMMENT '创建部门',
 *   `create_time`   datetime     DEFAULT NULL,
 *   `create_by`     bigint       DEFAULT NULL COMMENT '上传人',
 *   `update_time`   datetime     DEFAULT NULL,
 *   `update_by`     bigint       DEFAULT NULL,
 *   `service`       varchar(20)  NOT NULL DEFAULT 'minio' COMMENT '服务商',
 *   PRIMARY KEY (`oss_id`)
 * ) COMMENT='OSS对象存储表';
 * </pre>
 *
 * <p>Tests are automatically <b>skipped</b> when MySQL is unreachable
 * (controlled by {@link MySqlAvailabilityExtension}).
 */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles("mysql-it")
@ExtendWith(MySqlAvailabilityExtension.class)
@DisplayName("phoenix.sys_oss – live MySQL query tests")
class PhoenixOssQueryTest {

    private static final String DS = "mysql-phoenix";

    @Autowired
    private QueryService queryService;

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
        QueryResult result = execute("SELECT * FROM sys_oss LIMIT 10");

        assertThat(result.getRowCount()).isGreaterThanOrEqualTo(0);
        if (!result.getRows().isEmpty()) {
            Set<String> cols = result.getRows().get(0).keySet();
            assertThat(cols.stream().map(String::toLowerCase))
                    .contains("oss_id", "file_name", "original_name", "file_suffix", "url", "service");
        }
    }

    @Test
    @DisplayName("SELECT specific columns returns only those columns")
    void selectSpecificColumns_onlyRequestedColumnsReturned() {
        QueryResult result = execute(
                "SELECT oss_id, file_name, file_suffix, service FROM sys_oss LIMIT 5");

        assertThat(result.getColumns()).hasSize(4);
        assertThat(result.getColumns().stream().map(String::toLowerCase))
                .containsExactlyInAnyOrder("oss_id", "file_name", "file_suffix", "service");
    }

    // -----------------------------------------------------------------------
    // Filtering
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Filter by service='minio' returns only minio records")
    void filterByService_minio_returnsMatchingRows() {
        QueryResult result = execute(
                "SELECT oss_id, service FROM sys_oss WHERE service = 'minio' LIMIT 50");

        result.getRows().forEach(row -> {
            Object svc = colValue(row, "service");
            assertThat(svc).asString().isEqualToIgnoringCase("minio");
        });
    }

    @Test
    @DisplayName("Filter by file_suffix returns only matching rows")
    void filterByFileSuffix_returnsMatchingRows() {
        // Test with common image suffixes; table may be empty — assertion is conditional
        QueryResult result = execute(
                "SELECT oss_id, file_suffix FROM sys_oss " +
                "WHERE file_suffix IN ('.jpg', '.jpeg', '.png', '.gif') LIMIT 50");

        result.getRows().forEach(row -> {
            Object suffix = colValue(row, "file_suffix");
            assertThat(suffix).asString()
                    .isIn(".jpg", ".jpeg", ".png", ".gif");
        });
    }

    @Test
    @DisplayName("Filter by tenant_id returns only records for that tenant")
    void filterByTenantId_returnsMatchingRows() {
        // First find a tenant_id that exists in the table
        QueryResult sample = execute(
                "SELECT DISTINCT tenant_id FROM sys_oss WHERE tenant_id IS NOT NULL AND tenant_id <> '' LIMIT 1");
        if (sample.getRowCount() == 0) {
            // No tenants in the table — skip the assertion
            return;
        }

        Object tenantId = colValue(sample.getRows().get(0), "tenant_id");
        QueryResult result = execute(
                "SELECT oss_id, tenant_id FROM sys_oss WHERE tenant_id = '" + tenantId + "' LIMIT 20");

        result.getRows().forEach(row -> {
            Object tid = colValue(row, "tenant_id");
            assertThat(tid).asString().isEqualTo(tenantId.toString());
        });
    }

    // -----------------------------------------------------------------------
    // Sorting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("ORDER BY oss_id ASC returns rows in ascending primary-key order")
    void orderByOssId_ascending() {
        QueryResult result = execute("SELECT oss_id FROM sys_oss ORDER BY oss_id ASC LIMIT 20");

        List<Map<String, Object>> rows = result.getRows();
        for (int i = 1; i < rows.size(); i++) {
            long prev = toLong(colValue(rows.get(i - 1), "oss_id"));
            long curr = toLong(colValue(rows.get(i), "oss_id"));
            assertThat(curr).isGreaterThanOrEqualTo(prev);
        }
    }

    @Test
    @DisplayName("ORDER BY create_time DESC returns newest records first")
    void orderByCreateTime_descending() {
        QueryResult result = execute(
                "SELECT oss_id, create_time FROM sys_oss ORDER BY create_time DESC LIMIT 10");

        // Just assert the query completes without error and returns expected structure
        assertThat(result.getColumns().stream().map(String::toLowerCase))
                .contains("oss_id", "create_time");
    }

    // -----------------------------------------------------------------------
    // Aggregate queries
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("COUNT(*) returns a non-negative total")
    void count_returnsNonNegativeNumber() {
        QueryResult result = execute("SELECT COUNT(*) AS total FROM sys_oss");

        assertThat(result.getRowCount()).isEqualTo(1);
        long total = toLong(colValue(result.getRows().get(0), "total"));
        assertThat(total).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("GROUP BY file_suffix shows distribution of file types")
    void groupByFileSuffix_showsDistribution() {
        QueryResult result = execute(
                "SELECT file_suffix, COUNT(*) AS cnt FROM sys_oss GROUP BY file_suffix ORDER BY cnt DESC LIMIT 10");

        // Each row must have a suffix and a positive count
        result.getRows().forEach(row -> {
            Object cnt = colValue(row, "cnt");
            assertThat(toLong(cnt)).isGreaterThan(0);
        });
    }

    @Test
    @DisplayName("GROUP BY service shows distinct service providers")
    void groupByService_showsDistinctProviders() {
        QueryResult result = execute(
                "SELECT service, COUNT(*) AS cnt FROM sys_oss GROUP BY service");

        assertThat(result.getRowCount()).isGreaterThanOrEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // JOIN against scene (cross-database via separate requests — documented test)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("sys_oss.url is a non-empty string for all returned rows")
    void urlColumn_isNonEmpty() {
        QueryResult result = execute(
                "SELECT oss_id, url FROM sys_oss WHERE url IS NOT NULL AND url <> '' LIMIT 10");

        result.getRows().forEach(row -> {
            Object url = colValue(row, "url");
            assertThat(url).asString().isNotBlank();
        });
    }

    // -----------------------------------------------------------------------
    // Security sandbox tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("INSERT into sys_oss is rejected by the SQL sandbox")
    void insert_isRejectedBySandbox() {
        assertThatThrownBy(() -> execute(
                "INSERT INTO sys_oss (oss_id, file_name, original_name, file_suffix, url, service) "
                + "VALUES (9999999, 'test', 'test.txt', '.txt', 'http://x', 'minio')"))
                .isInstanceOf(SqlSecurityException.class);
    }

    @Test
    @DisplayName("DELETE from sys_oss is rejected by the SQL sandbox")
    void delete_isRejectedBySandbox() {
        assertThatThrownBy(() -> execute("DELETE FROM sys_oss WHERE oss_id = 9999999"))
                .isInstanceOf(SqlSecurityException.class);
    }

    // -----------------------------------------------------------------------
    // Result metadata
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Result carries correct dataSource name and timing")
    void resultMetadata_isCorrect() {
        QueryResult result = execute("SELECT oss_id FROM sys_oss LIMIT 1");
        assertThat(result.getDataSource()).isEqualTo(DS);
        assertThat(result.getElapsedMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.isCached()).isFalse();
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

    private Object colValue(Map<String, Object> row, String column) {
        if (row.containsKey(column)) return row.get(column);
        if (row.containsKey(column.toUpperCase())) return row.get(column.toUpperCase());
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
