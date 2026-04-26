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
        hc.setConnectionTimeout(30_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);

        if (cfg.getDriverClassName() != null && !cfg.getDriverClassName().isBlank()) {
            hc.setDriverClassName(cfg.getDriverClassName());
        } else {
            // Attempt auto-detection by JDBC URL scheme
            String url = cfg.getUrl() != null ? cfg.getUrl().toLowerCase() : "";
            if (url.contains(":mysql:")) {
                hc.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else if (url.contains(":oracle:")) {
                hc.setDriverClassName("oracle.jdbc.OracleDriver");
            } else if (url.contains(":sqlserver:")) {
                hc.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            } else if (url.contains(":sqlite:")) {
                hc.setDriverClassName("org.sqlite.JDBC");
            } else if (url.contains(":h2:")) {
                hc.setDriverClassName("org.h2.Driver");
            }
        }

        return hc;
    }
}
