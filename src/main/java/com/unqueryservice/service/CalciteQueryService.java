package com.unqueryservice.service;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.exception.QueryServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.schema.SchemaPlus;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Core Calcite-backed query execution service.
 *
 * <p>For each query, this service:
 * <ol>
 *   <li>Creates a Calcite JDBC connection wrapping the target {@link DataSource}.</li>
 *   <li>Registers a {@link JdbcSchema} (a Calcite JDBC adapter) so Calcite can
 *       plan and optimise queries against the real JDBC source.</li>
 *   <li>Executes the query via a prepared statement to support bind parameters.</li>
 *   <li>Maps the {@link ResultSet} into a list of {@code Map<String, Object>} rows.</li>
 * </ol>
 *
 * <p>A new {@link CalciteConnection} is opened per query (connection-per-request
 * pattern) and closed in a finally block. The underlying JDBC connection is
 * managed by HikariCP and returned to the pool automatically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalciteQueryService {

    /** Calcite JDBC driver class name. */
    private static final String CALCITE_DRIVER = "org.apache.calcite.jdbc.Driver";

    private final DataSourceRegistry dataSourceRegistry;
    private final QueryServiceProperties properties;

    /**
     * Executes the given SQL against the named data source.
     *
     * @param dataSourceName the logical name of the target data source
     * @param sql            the SQL statement to execute (already validated)
     * @param params         positional bind parameters (may be null or empty)
     * @param maxRows        maximum number of rows to return
     * @return               ordered list of result rows
     */
    public List<Map<String, Object>> execute(String dataSourceName,
                                              String sql,
                                              List<Object> params,
                                              int maxRows) {

        DataSource targetDs = dataSourceRegistry.get(dataSourceName);

        // Load the Calcite driver (idempotent)
        try {
            Class.forName(CALCITE_DRIVER);
        } catch (ClassNotFoundException ex) {
            throw new QueryServiceException("Calcite JDBC driver not found on classpath", ex);
        }

        Properties calciteProps = new Properties();
        calciteProps.setProperty("lex", "MYSQL");   // Use MySQL lexer for broader SQL compatibility

        try (Connection calciteConn = DriverManager.getConnection("jdbc:calcite:", calciteProps)) {
            CalciteConnection cc = calciteConn.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = cc.getRootSchema();

            // Register the target JDBC data source as a named schema
            JdbcSchema jdbcSchema = JdbcSchema.create(rootSchema, dataSourceName, targetDs, null, null);
            rootSchema.add(dataSourceName, jdbcSchema);

            // Set the default schema so unqualified table names resolve correctly
            cc.setSchema(dataSourceName);

            return executeQuery(cc, sql, params, maxRows);

        } catch (SQLException ex) {
            throw new QueryServiceException(
                    "Query execution failed on data source '" + dataSourceName + "': " + ex.getMessage(), ex);
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

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
