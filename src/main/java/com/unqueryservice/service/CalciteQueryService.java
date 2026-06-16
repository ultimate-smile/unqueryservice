package com.unqueryservice.service;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.exception.QueryServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Core JDBC-backed query execution service.
 *
 * <p>The SQL sandbox still validates statements before this class is called; this
 * class executes the validated SQL natively against the selected HikariCP-backed
 * data source. Native JDBC execution preserves vendor-specific Oracle and SQL
 * Server syntax while keeping a single result mapping contract for the REST API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalciteQueryService {

    private final DataSourceRegistry dataSourceRegistry;

    /** Executes the given SQL against the named data source. */
    public List<Map<String, Object>> execute(String dataSourceName,
                                              String sql,
                                              List<Object> params,
                                              int maxRows) {
        DataSource targetDs = dataSourceRegistry.get(dataSourceName);
        try (Connection conn = targetDs.getConnection()) {
            return executeQuery(conn, sql, params, maxRows);
        } catch (SQLException ex) {
            throw new QueryServiceException(
                    "Query execution failed on data source '" + dataSourceName + "': " + ex.getMessage(), ex);
        }
    }

    /** Builds a dialect-specific paged wrapper query for the named data source. */
    public String pageSql(String dataSourceName, String sql, int limit, int offset) {
        QueryServiceProperties.DataSourceConfig cfg = dataSourceRegistry.config(dataSourceName);
        return DatabaseDialect.from(cfg.getType(), cfg.getUrl()).pageSql(sql, limit, offset);
    }

    private List<Map<String, Object>> executeQuery(Connection conn,
                                                   String sql,
                                                   List<Object> params,
                                                   int maxRows) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setMaxRows(maxRows);
            stmt.setFetchSize(Math.min(maxRows, 1000));

            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return mapResultSet(rs, maxRows);
            }
        }
    }

    private List<Map<String, Object>> mapResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        List<String> colNames = new ArrayList<>(colCount);
        for (int i = 1; i <= colCount; i++) {
            colNames.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowsFetched = 0;

        while (rs.next() && rowsFetched < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                row.put(colNames.get(i - 1), rs.getObject(i));
            }
            rows.add(row);
            rowsFetched++;
        }

        return rows;
    }
}
