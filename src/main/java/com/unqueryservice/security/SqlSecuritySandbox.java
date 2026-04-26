package com.unqueryservice.security;

import com.unqueryservice.exception.SqlSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SQL security sandbox that validates a raw SQL string before it is executed.
 *
 * <p>Validation strategy:
 * <ol>
 *   <li>Parse the statement with Apache Calcite's SQL parser (catches syntax errors
 *       and detects the statement kind).</li>
 *   <li>Allow only {@code SELECT} statements — all DDL and DML is rejected.</li>
 *   <li>Keyword blocklist check against a deny-list of dangerous patterns.</li>
 *   <li>Length cap (delegated to bean validation on the request DTO, but also
 *       enforced here as a second line of defence).</li>
 * </ol>
 */
@Slf4j
@Component
public class SqlSecuritySandbox {

    private static final int MAX_SQL_LENGTH = 10_000;

    /**
     * Dangerous SQL keywords / patterns that must never appear in a query,
     * even inside a SELECT.
     */
    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "INTO OUTFILE", "INTO DUMPFILE",    // MySQL file export
            "LOAD_FILE",                         // MySQL file read
            "SLEEP(", "BENCHMARK(",              // MySQL time-based injection
            "WAITFOR DELAY", "WAITFOR TIME",     // SQL Server time-based injection
            "DBMS_PIPE", "UTL_FILE",             // Oracle dangerous packages
            "XP_CMDSHELL", "SP_EXECUTESQL",      // SQL Server execution
            "EXEC(", "EXECUTE(",                  // generic execute
            "OPENROWSET", "OPENDATASOURCE"        // SQL Server lateral data access
    );

    /**
     * Validates the SQL string.  Throws {@link SqlSecurityException} if any
     * rule is violated; returns silently on success.
     *
     * @param sql the raw SQL received from the client
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlSecurityException("SQL statement must not be empty");
        }

        if (sql.length() > MAX_SQL_LENGTH) {
            throw new SqlSecurityException("SQL statement exceeds maximum allowed length of " + MAX_SQL_LENGTH);
        }

        // Keyword blocklist (case-insensitive)
        String upper = sql.toUpperCase(Locale.ROOT);
        for (String blocked : BLOCKED_KEYWORDS) {
            if (upper.contains(blocked)) {
                throw new SqlSecurityException("SQL contains a forbidden keyword: " + blocked);
            }
        }

        // Parse using Calcite to detect statement kind and syntax errors.
        // parseStatement() is used (not parseQuery()) so that DDL/DML statements
        // parse successfully and reach the kind-check below rather than failing
        // with a confusing syntax error message.
        SqlParser parser = SqlParser.create(sql, SqlParser.Config.DEFAULT);
        SqlNode node;
        try {
            node = parser.parseStmt();
        } catch (SqlParseException ex) {
            throw new SqlSecurityException("SQL parse error: " + ex.getMessage());
        }

        // Only SELECT (and its sub-types like UNION, INTERSECT, EXCEPT) are allowed
        SqlKind kind = node.getKind();
        if (!isAllowedKind(kind)) {
            throw new SqlSecurityException(
                    "Only SELECT statements are permitted. Received statement kind: " + kind);
        }

        log.debug("SQL passed security validation (kind={})", kind);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private boolean isAllowedKind(SqlKind kind) {
        return kind == SqlKind.SELECT
                || kind == SqlKind.ORDER_BY
                || kind == SqlKind.UNION
                || kind == SqlKind.INTERSECT
                || kind == SqlKind.EXCEPT
                || kind == SqlKind.VALUES;
    }
}
