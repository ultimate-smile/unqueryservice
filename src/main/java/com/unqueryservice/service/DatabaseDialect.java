package com.unqueryservice.service;

import java.util.Locale;

/** SQL dialect helpers for native JDBC execution against configured data sources. */
public enum DatabaseDialect {
    MYSQL,
    ORACLE,
    SQLSERVER,
    SQLITE,
    H2,
    GENERIC;

    public static DatabaseDialect from(String type, String jdbcUrl) {
        String value = ((type == null ? "" : type) + " " + (jdbcUrl == null ? "" : jdbcUrl)).toLowerCase(Locale.ROOT);
        if (value.contains("mysql")) return MYSQL;
        if (value.contains("oracle")) return ORACLE;
        if (value.contains("sqlserver") || value.contains("mssql")) return SQLSERVER;
        if (value.contains("sqlite")) return SQLITE;
        if (value.contains("h2")) return H2;
        return GENERIC;
    }

    public String pageSql(String sql, int limit, int offset) {
        return switch (this) {
            case ORACLE -> "SELECT * FROM (" + sql + ") _page_wrap OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
            case SQLSERVER -> "SELECT * FROM (" + sql + ") _page_wrap ORDER BY (SELECT 0) OFFSET " + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
            default -> "SELECT * FROM (" + sql + ") _page_wrap LIMIT " + limit + " OFFSET " + offset;
        };
    }
}
