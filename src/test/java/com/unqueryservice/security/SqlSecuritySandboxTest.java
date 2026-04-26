package com.unqueryservice.security;

import com.unqueryservice.exception.SqlSecurityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SqlSecuritySandbox}.
 */
class SqlSecuritySandboxTest {

    private SqlSecuritySandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new SqlSecuritySandbox();
    }

    // ------------------------------------------------------------------
    // Valid SELECT statements – should pass without exception
    // ------------------------------------------------------------------

    @Test
    void simpleSelect_passes() {
        assertThatNoException().isThrownBy(() -> sandbox.validate("SELECT id, name FROM users"));
    }

    @Test
    void selectWithWhere_passes() {
        assertThatNoException().isThrownBy(() ->
                sandbox.validate("SELECT * FROM orders WHERE status = 'active'"));
    }

    @Test
    void selectWithJoin_passes() {
        assertThatNoException().isThrownBy(() ->
                sandbox.validate("SELECT u.id, o.total FROM users u JOIN orders o ON u.id = o.user_id"));
    }

    @Test
    void selectWithSubquery_passes() {
        assertThatNoException().isThrownBy(() ->
                sandbox.validate("SELECT * FROM (SELECT id, amount FROM payments) p WHERE p.amount > 100"));
    }

    @Test
    void unionSelect_passes() {
        assertThatNoException().isThrownBy(() ->
                sandbox.validate("SELECT id FROM tableA UNION SELECT id FROM tableB"));
    }

    @Test
    void selectWithOrderByAndLimit_passes() {
        assertThatNoException().isThrownBy(() ->
                sandbox.validate("SELECT id, name FROM users ORDER BY name LIMIT 50"));
    }

    // ------------------------------------------------------------------
    // Blocked DML / DDL statements
    // ------------------------------------------------------------------

    /**
     * DML (INSERT/UPDATE/DELETE) is parsed by the core Calcite parser and then
     * rejected at the statement-kind check.  DDL (DROP/CREATE/TRUNCATE/ALTER)
     * is not in the core parser and fails at parse time – both outcomes produce
     * a {@link SqlSecurityException}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO users (name) VALUES ('evil')",
            "UPDATE users SET name = 'evil' WHERE 1=1",
            "DELETE FROM users",
            "DROP TABLE users",
            "CREATE TABLE evil (id INT)",
            "TRUNCATE TABLE users",
            "ALTER TABLE users ADD COLUMN evil VARCHAR(255)",
    })
    void dmlDdl_isRejected(String sql) {
        assertThatThrownBy(() -> sandbox.validate(sql))
                .isInstanceOf(SqlSecurityException.class);
    }

    // ------------------------------------------------------------------
    // Blocked dangerous keywords
    // ------------------------------------------------------------------

    @Test
    void blockedKeyword_intoOutfile_isRejected() {
        assertThatThrownBy(() -> sandbox.validate("SELECT * FROM users INTO OUTFILE '/tmp/dump'"))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("forbidden keyword");
    }

    @Test
    void blockedKeyword_sleep_isRejected() {
        assertThatThrownBy(() -> sandbox.validate("SELECT SLEEP(5) FROM dual"))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("forbidden keyword");
    }

    @Test
    void blockedKeyword_xpCmdshell_isRejected() {
        assertThatThrownBy(() -> sandbox.validate("SELECT XP_CMDSHELL('dir') FROM dual"))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("forbidden keyword");
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    void nullSql_isRejected() {
        assertThatThrownBy(() -> sandbox.validate(null))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void blankSql_isRejected() {
        assertThatThrownBy(() -> sandbox.validate("   "))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void syntacticallyInvalidSql_isRejected() {
        assertThatThrownBy(() -> sandbox.validate("SELECT FROM WHERE"))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("SQL parse error");
    }

    @Test
    void tooLongSql_isRejected() {
        String longSql = "SELECT " + "a,".repeat(5001) + " 1 FROM t";
        assertThatThrownBy(() -> sandbox.validate(longSql))
                .isInstanceOf(SqlSecurityException.class)
                .hasMessageContaining("exceeds maximum allowed length");
    }
}
