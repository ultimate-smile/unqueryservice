package com.unqueryservice.service;

import com.unqueryservice.config.DataSourceRegistry;
import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.exception.QueryServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.time.OffsetDateTime;
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

            int parameterMarkerCount = countParameterMarkers(sql);
            int suppliedParamCount = params == null ? 0 : params.size();
            if (suppliedParamCount < parameterMarkerCount) {
                throw new SQLException("Missing SQL parameters: expected " + parameterMarkerCount
                        + " but received " + suppliedParamCount);
            }
            if (params != null) {
                for (int i = 0; i < parameterMarkerCount; i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                return mapResultSet(rs, maxRows);
            }
        }
    }

    static int countParameterMarkers(String sql) {
        int count = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n' || current == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingleQuote) {
                if (current == '\\' && next != '\0') {
                    i++;
                } else if (current == '\'' && next == '\'') {
                    i++;
                } else if (current == '\'') {
                    inSingleQuote = false;
                }
                continue;
            }
            if (inDoubleQuote) {
                if (current == '\\' && next != '\0') {
                    i++;
                } else if (current == '"' && next == '"') {
                    i++;
                } else if (current == '"') {
                    inDoubleQuote = false;
                }
                continue;
            }
            if (inBacktick) {
                if (current == '`') {
                    inBacktick = false;
                }
                continue;
            }

            if (current == '-' && next == '-') {
                inLineComment = true;
                i++;
            } else if (current == '#') {
                inLineComment = true;
            } else if (current == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else if (current == '\'') {
                inSingleQuote = true;
            } else if (current == '"') {
                inDoubleQuote = true;
            } else if (current == '`') {
                inBacktick = true;
            } else if (current == '?') {
                count++;
            }
        }
        return count;
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
                row.put(colNames.get(i - 1), toJsonSafeValue(rs, meta, i));
            }
            rows.add(row);
            rowsFetched++;
        }

        return rows;
    }

    static Object toJsonSafeValue(ResultSet rs, ResultSetMetaData meta, int columnIndex) throws SQLException {
        int sqlType = meta.getColumnType(columnIndex);
        return switch (sqlType) {
            case Types.DATE -> {
                java.sql.Date value = rs.getDate(columnIndex);
                yield value == null ? null : value.toLocalDate();
            }
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> {
                Time value = rs.getTime(columnIndex);
                yield value == null ? null : value.toLocalTime();
            }
            case Types.TIMESTAMP -> {
                Timestamp value = rs.getTimestamp(columnIndex);
                yield value == null ? null : value.toLocalDateTime();
            }
            case Types.TIMESTAMP_WITH_TIMEZONE -> readOffsetDateTime(rs, columnIndex);
            case Types.BLOB -> readBlob(rs.getBlob(columnIndex));
            case Types.CLOB, Types.NCLOB -> readClob(rs.getClob(columnIndex));
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> rs.getBytes(columnIndex);
            default -> normalizeJdbcObject(rs.getObject(columnIndex));
        };
    }

    private static Object readOffsetDateTime(ResultSet rs, int columnIndex) throws SQLException {
        try {
            return rs.getObject(columnIndex, OffsetDateTime.class);
        } catch (SQLFeatureNotSupportedException | SQLDataException ex) {
            Timestamp value = rs.getTimestamp(columnIndex);
            return value == null ? null : value.toLocalDateTime();
        }
    }

    private static Object normalizeJdbcObject(Object value) throws SQLException {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof java.time.temporal.TemporalAccessor
                || value instanceof UUID
                || value instanceof byte[]) {
            return value;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        if (value instanceof Blob blob) {
            return readBlob(blob);
        }
        if (value instanceof Clob clob) {
            return readClob(clob);
        }
        if (value instanceof SQLXML sqlxml) {
            return sqlxml.getString();
        }
        if (value instanceof Array array) {
            return normalizeJdbcObject(array.getArray());
        }
        if (value instanceof InputStream || value instanceof Reader) {
            return value.toString();
        }
        if (value instanceof BigDecimal || value instanceof URL) {
            return value.toString();
        }
        return value.toString();
    }

    private static byte[] readBlob(Blob blob) throws SQLException {
        if (blob == null) {
            return null;
        }
        try (InputStream inputStream = blob.getBinaryStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw new SQLException("Failed to read BLOB column", ex);
        }
    }

    private static String readClob(Clob clob) throws SQLException {
        if (clob == null) {
            return null;
        }
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException ex) {
            throw new SQLException("Failed to read CLOB column", ex);
        }
    }
}
