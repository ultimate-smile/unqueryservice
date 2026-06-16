package com.unqueryservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseDialectTest {

    @Test
    void detectsOracleFromTypeAndBuildsOraclePagination() {
        String sql = DatabaseDialect.from("oracle", null)
                .pageSql("SELECT id FROM users", 25, 50);

        assertThat(sql).contains("OFFSET 50 ROWS FETCH NEXT 25 ROWS ONLY");
    }

    @Test
    void detectsSqlServerFromUrlAndBuildsSqlServerPagination() {
        String sql = DatabaseDialect.from(null, "jdbc:sqlserver://localhost;databaseName=app")
                .pageSql("SELECT id FROM users", 10, 20);

        assertThat(sql).contains("ORDER BY (SELECT 0) OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY");
    }

    @Test
    void defaultsToLimitOffsetForMysqlCompatibleDialects() {
        String sql = DatabaseDialect.from("mysql", null)
                .pageSql("SELECT id FROM users", 5, 15);

        assertThat(sql).endsWith("LIMIT 5 OFFSET 15");
    }
}
