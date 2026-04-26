package com.unqueryservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level configuration properties bound from {@code application.yml}
 * under the prefix {@code query-service}.
 *
 * <p>Authentication and permission control are handled externally by ThingsBoard.
 * This service trusts all inbound requests.
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

    /** Named data-source configurations keyed by logical name. */
    private Map<String, DataSourceConfig> dataSources = new LinkedHashMap<>();

    @Data
    public static class DataSourceConfig {
        private String type;       // mysql | oracle | sqlserver | sqlite | h2
        private String url;
        private String username;
        private String password;
        private String driverClassName;
        private int maxPoolSize = 10;
    }
}
