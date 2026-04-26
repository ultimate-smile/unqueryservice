package com.unqueryservice.util;

import com.unqueryservice.exception.SqlSecurityException;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * SQL security sandbox that validates a raw SQL string before it is executed.
 *
 * <p>Validation strategy:
 * <ol>
 *   <li>Length cap — max 10,000 characters.</li>
 *   <li>Keyword deny-list — blocks known injection and data-exfiltration patterns.</li>
 *   <li>Calcite AST parse — detects syntax errors and identifies the statement kind.</li>
 *   <li>Statement kind check — only {@code SELECT} and set-operation variants are allowed.</li>
 * </ol>
 *
 * <p>Even if a statement passes the sandbox, all queries additionally use JDBC
 * prepared statements with bind parameters, so injection via parameter values is
 * structurally impossible.
 */
@Slf4j
@Component
public class SqlSecuritySandbox {

    private static final int MAX_SQL_LENGTH = 10_000;

    private static final Set<String> BLOCKED_KEYWORDS = Set.of(
            "INTO OUTFILE", "INTO DUMPFILE",
            "LOAD_FILE",
            "SLEEP(", "BENCHMARK(",
            "WAITFOR DELAY", "WAITFOR TIME",
            "DBMS_PIPE", "UTL_FILE",
            "XP_CMDSHELL", "SP_EXECUTESQL",
            "EXEC(", "EXECUTE(",
            "OPENROWSET", "OPENDATASOURCE"
    );

    /**
     * Validates the SQL string. Throws {@link SqlSecurityException} if any rule is
     * violated; returns silently on success.
     *
     * @param sql the raw SQL received from the client
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new SqlSecurityException("SQL statement must not be empty");
        }

        if (sql.length() > MAX_SQL_LENGTH) {
            throw new SqlSecurityException(
                    "SQL statement exceeds maximum allowed length of " + MAX_SQL_LENGTH);
        }

        String upper = sql.toUpperCase(Locale.ROOT);
        for (String blocked : BLOCKED_KEYWORDS) {
            if (upper.contains(blocked)) {
                throw new SqlSecurityException("SQL contains a forbidden keyword: " + blocked);
            }
        }

        SqlParser parser = SqlParser.create(sql, SqlParser.Config.DEFAULT);
        SqlNode node;
        try {
            // parseStmt() accepts DML/DDL (unlike parseQuery()) so they reach the kind check
            // and produce the clearer "Only SELECT statements are permitted" message.
            node = parser.parseStmt();
        } catch (SqlParseException ex) {
            throw new SqlSecurityException("SQL parse error: " + ex.getMessage());
        }

        SqlKind kind = node.getKind();
        if (!isAllowedKind(kind)) {
            throw new SqlSecurityException(
                    "Only SELECT statements are permitted. Received statement kind: " + kind);
        }

        log.debug("SQL passed security validation (kind={})", kind);
    }

    private boolean isAllowedKind(SqlKind kind) {
        return kind == SqlKind.SELECT
                || kind == SqlKind.ORDER_BY
                || kind == SqlKind.UNION
                || kind == SqlKind.INTERSECT
                || kind == SqlKind.EXCEPT
                || kind == SqlKind.VALUES;
    }
}
