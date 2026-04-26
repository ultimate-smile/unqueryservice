package com.unqueryservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level configuration properties bound from {@code application.yml}
 * under the prefix {@code query-service}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "query-service")
public class QueryServiceProperties {

    /** Default row limit applied when the client does not specify one. */
    private int defaultRowLimit = 1000;

    /** Absolute maximum rows the service will ever return per query. */
    private int maxRowLimit = 10_000;

    /** Cache TTL in seconds (0 = caching disabled). */
    private long cacheTtlSeconds = 60;

    /** JWT configuration. */
    private Jwt jwt = new Jwt();

    /** Named data-source configurations keyed by logical name. */
    private Map<String, DataSourceConfig> dataSources = new LinkedHashMap<>();

    /** Per-role or per-user row- and column-level permissions. */
    private Map<String, PermissionConfig> permissions = new LinkedHashMap<>();

    @Data
    public static class Jwt {
        private String secret = "CHANGE_ME_IN_PRODUCTION_THIS_MUST_BE_AT_LEAST_256_BITS_LONG_STRING";
        private long expirationMs = 3_600_000; // 1 hour
    }

    @Data
    public static class DataSourceConfig {
        private String type;       // mysql | oracle | sqlserver | sqlite | h2
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maxPoolSize = 10;
    }

    @Data
    public static class PermissionConfig {
        /** Roles allowed to query this data source. Empty means all authenticated users. */
        private List<String> allowedRoles;
        /** Column names that are masked for non-admin users. */
        private List<String> maskedColumns;
        /** A SQL WHERE snippet appended to every query for row-level filtering. */
        private String rowFilter;
    }
}
