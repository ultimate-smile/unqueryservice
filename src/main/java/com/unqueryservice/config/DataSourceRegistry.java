package com.unqueryservice.config;

import com.unqueryservice.exception.DataSourceNotFoundException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Builds and manages a pool of {@link HikariDataSource} instances from
 * the properties defined under {@code query-service.data-sources}.
 *
 * <p>Each data source is identified by a logical name (e.g. "mysql-prod",
 * "sqlite-local") that clients include in {@link com.unqueryservice.model.QueryRequest}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSourceRegistry {

    private final QueryServiceProperties properties;

    /** Live data-source pool, keyed by logical name. */
    private final Map<String, HikariDataSource> registry = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        properties.getDataSources().forEach((name, cfg) -> {
            try {
                HikariConfig hikariConfig = buildHikariConfig(name, cfg);
                HikariDataSource ds = new HikariDataSource(hikariConfig);
                registry.put(name, ds);
                log.info("Registered data source '{}' (type={})", name, cfg.getType());
            } catch (Exception ex) {
                log.error("Failed to initialise data source '{}': {}", name, ex.getMessage(), ex);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        registry.values().forEach(ds -> {
            try {
                ds.close();
            } catch (Exception ignored) {
            }
        });
        registry.clear();
        log.info("All data sources closed");
    }

    /**
     * Retrieves a configured data source by its logical name.
     *
     * @throws DataSourceNotFoundException if the name is not registered
     */
    public DataSource get(String name) {
        HikariDataSource ds = registry.get(name);
        if (ds == null) {
            throw new DataSourceNotFoundException(name);
        }
        return ds;
    }

    /** Returns the configuration associated with a registered logical name. */
    public QueryServiceProperties.DataSourceConfig config(String name) {
        if (!registry.containsKey(name)) {
            throw new DataSourceNotFoundException(name);
        }
        return properties.getDataSources().get(name);
    }

    /** Returns an immutable view of all registered logical names. */
    public Set<String> names() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private HikariConfig buildHikariConfig(String name, QueryServiceProperties.DataSourceConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setPoolName("hkpool-" + name);
        hc.setJdbcUrl(cfg.getUrl());
        hc.setUsername(cfg.getUsername());
        hc.setPassword(cfg.getPassword());
        hc.setMaximumPoolSize(cfg.getMaxPoolSize());
        hc.setMinimumIdle(cfg.getMinIdle());
        hc.setConnectionTimeout(30_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);

        if (cfg.getConnectionTestQuery() != null && !cfg.getConnectionTestQuery().isBlank()) {
            hc.setConnectionTestQuery(cfg.getConnectionTestQuery());
        }
        cfg.getDataSourceProperties().forEach(hc::addDataSourceProperty);

        String driverClassName = resolveDriverClassName(cfg);
        if (driverClassName != null) {
            hc.setDriverClassName(driverClassName);
        }

        return hc;
    }

    static String resolveDriverClassName(QueryServiceProperties.DataSourceConfig cfg) {
        if (cfg.getDriverClassName() != null && !cfg.getDriverClassName().isBlank()) {
            return cfg.getDriverClassName();
        }

        String type = cfg.getType() != null ? cfg.getType().toLowerCase() : "";
        String url = cfg.getUrl() != null ? cfg.getUrl().toLowerCase() : "";
        if ("mysql".equals(type) || url.contains(":mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        } else if ("oracle".equals(type) || url.contains(":oracle:")) {
            return "oracle.jdbc.OracleDriver";
        } else if ("sqlserver".equals(type) || "mssql".equals(type) || url.contains(":sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        } else if ("sqlite".equals(type) || url.contains(":sqlite:")) {
            return "org.sqlite.JDBC";
        } else if ("h2".equals(type) || url.contains(":h2:")) {
            return "org.h2.Driver";
        }
        return null;
    }
}
