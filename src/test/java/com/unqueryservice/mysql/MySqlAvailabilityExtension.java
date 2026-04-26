package com.unqueryservice.mysql;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Logger;

/**
 * JUnit 5 extension that probes MySQL connectivity before the test class runs.
 *
 * <p>If MySQL is unreachable the entire test class is skipped via
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue(boolean, String)},
 * so the build stays green in environments without a live MySQL server (e.g. CI).
 */
public class MySqlAvailabilityExtension implements BeforeAllCallback {

    private static final Logger LOG = Logger.getLogger(MySqlAvailabilityExtension.class.getName());

    static final String MYSQL_HOST = System.getenv().getOrDefault("MYSQL_HOST", "127.0.0.1");
    static final String MYSQL_USER = System.getenv().getOrDefault("MYSQL_USER", "root");
    static final String MYSQL_PASSWORD = System.getenv().getOrDefault("MYSQL_PASSWORD", "W2pLIKgtWz4XunL9pja");

    private static final Namespace NS = Namespace.create(MySqlAvailabilityExtension.class);
    private static final String KEY = "mysql_available";

    @Override
    public void beforeAll(ExtensionContext context) {
        ExtensionContext.Store root = context.getRoot().getStore(NS);
        // Probe once per JVM run and cache the result
        boolean available = root.getOrComputeIfAbsent(KEY, k -> probe(), Boolean.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(available,
                "MySQL at " + MYSQL_HOST + ":3306 is not reachable — skipping live MySQL tests");
    }

    private boolean probe() {
        String url = "jdbc:mysql://" + MYSQL_HOST
                + ":3306/?useSSL=false&serverTimezone=UTC&connectTimeout=2000&socketTimeout=3000";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = DriverManager.getConnection(url, MYSQL_USER, MYSQL_PASSWORD)) {
                LOG.info("MySQL connectivity confirmed at " + MYSQL_HOST + ":3306");
                return true;
            }
        } catch (Exception ex) {
            LOG.warning("MySQL not reachable: " + ex.getMessage());
            return false;
        }
    }
}
