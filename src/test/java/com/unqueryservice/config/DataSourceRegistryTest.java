package com.unqueryservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRegistryTest {

    @Test
    void resolvesOracleDriverFromType() {
        QueryServiceProperties.DataSourceConfig cfg = new QueryServiceProperties.DataSourceConfig();
        cfg.setType("oracle");
        cfg.setUrl("jdbc:oracle:thin:@//localhost:1521/XEPDB1");

        assertThat(DataSourceRegistry.resolveDriverClassName(cfg))
                .isEqualTo("oracle.jdbc.OracleDriver");
    }

    @Test
    void resolvesSqlServerDriverFromType() {
        QueryServiceProperties.DataSourceConfig cfg = new QueryServiceProperties.DataSourceConfig();
        cfg.setType("sqlserver");
        cfg.setUrl("jdbc:sqlserver://localhost:1433;databaseName=app");

        assertThat(DataSourceRegistry.resolveDriverClassName(cfg))
                .isEqualTo("com.microsoft.sqlserver.jdbc.SQLServerDriver");
    }

    @Test
    void explicitDriverClassNameWins() {
        QueryServiceProperties.DataSourceConfig cfg = new QueryServiceProperties.DataSourceConfig();
        cfg.setType("oracle");
        cfg.setDriverClassName("example.CustomDriver");

        assertThat(DataSourceRegistry.resolveDriverClassName(cfg))
                .isEqualTo("example.CustomDriver");
    }
}
