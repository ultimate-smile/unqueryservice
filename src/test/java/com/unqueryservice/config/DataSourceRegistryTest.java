package com.unqueryservice.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceRegistryTest {

    @Test
    void appliesConfiguredJdbcConnectionProperties() {
        QueryServiceProperties properties = new QueryServiceProperties();
        DataSourceRegistry registry = new DataSourceRegistry(properties);
        QueryServiceProperties.DataSourceConfig cfg = new QueryServiceProperties.DataSourceConfig();
        cfg.setType("oracle");
        cfg.setUrl("jdbc:oracle:thin:@//localhost:1521/XEPDB1");
        cfg.setUsername("sys");
        cfg.setPassword("oracle");
        cfg.getConnectionProperties().put("internal_logon", "sysdba");

        HikariConfig hikariConfig = registry.buildHikariConfig("oracle-admin", cfg);

        assertThat(hikariConfig.getDataSourceProperties())
                .containsEntry("internal_logon", "sysdba");
    }
}
