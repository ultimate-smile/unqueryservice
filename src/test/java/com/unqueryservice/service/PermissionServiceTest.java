package com.unqueryservice.service;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.exception.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PermissionService}.
 */
class PermissionServiceTest {

    private PermissionService permissionService;
    private QueryServiceProperties properties;

    @BeforeEach
    void setUp() {
        properties = new QueryServiceProperties();
        permissionService = new PermissionService(properties);

        // Configure a single data source with ROLE_ADMIN and ROLE_ANALYST access
        QueryServiceProperties.PermissionConfig perm = new QueryServiceProperties.PermissionConfig();
        perm.setAllowedRoles(List.of("ROLE_ADMIN", "ROLE_ANALYST"));
        perm.setMaskedColumns(List.of("credit_card", "ssn"));
        perm.setRowFilter("");

        properties.getPermissions().put("protected-ds", perm);
    }

    // ------------------------------------------------------------------
    // Access checks
    // ------------------------------------------------------------------

    @Test
    void adminCanAccess() {
        Authentication auth = auth("admin", "ROLE_ADMIN");
        assertThatNoException().isThrownBy(() ->
                permissionService.checkDataSourceAccess("protected-ds", auth));
    }

    @Test
    void analystCanAccess() {
        Authentication auth = auth("analyst", "ROLE_ANALYST");
        assertThatNoException().isThrownBy(() ->
                permissionService.checkDataSourceAccess("protected-ds", auth));
    }

    @Test
    void viewerIsRejected() {
        Authentication auth = auth("viewer", "ROLE_VIEWER");
        assertThatThrownBy(() -> permissionService.checkDataSourceAccess("protected-ds", auth))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("not authorised");
    }

    @Test
    void noPermConfigMeansOpenAccess() {
        Authentication auth = auth("anyone", "ROLE_GUEST");
        // "open-ds" has no permission config → access is granted
        assertThatNoException().isThrownBy(() ->
                permissionService.checkDataSourceAccess("open-ds", auth));
    }

    // ------------------------------------------------------------------
    // Field masking
    // ------------------------------------------------------------------

    @Test
    void nonAdminSeesColumnsMasked() {
        Authentication auth = auth("analyst", "ROLE_ANALYST");
        List<Map<String, Object>> rows = List.of(row("credit_card", "1234-5678", "name", "Alice"));

        List<Map<String, Object>> result = permissionService.applyFieldMasking("protected-ds", auth, rows);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("credit_card", "***");
        assertThat(result.get(0)).containsEntry("name", "Alice");
    }

    @Test
    void adminSeesUnmaskedColumns() {
        Authentication auth = auth("admin", "ROLE_ADMIN");
        List<Map<String, Object>> rows = List.of(row("credit_card", "1234-5678", "name", "Bob"));

        List<Map<String, Object>> result = permissionService.applyFieldMasking("protected-ds", auth, rows);

        assertThat(result.get(0)).containsEntry("credit_card", "1234-5678");
    }

    @Test
    void maskingSkippedWhenNoConfig() {
        Authentication auth = auth("user", "ROLE_VIEWER");
        List<Map<String, Object>> rows = List.of(row("secret", "value", "name", "Bob"));

        List<Map<String, Object>> result = permissionService.applyFieldMasking("open-ds", auth, rows);

        assertThat(result.get(0)).containsEntry("secret", "value");
    }

    // ------------------------------------------------------------------
    // Row filter
    // ------------------------------------------------------------------

    @Test
    void rowFilterReturnedWhenConfigured() {
        QueryServiceProperties.PermissionConfig perm = new QueryServiceProperties.PermissionConfig();
        perm.setRowFilter("tenant_id = 'acme'");
        properties.getPermissions().put("tenant-ds", perm);

        assertThat(permissionService.getRowFilter("tenant-ds")).isEqualTo("tenant_id = 'acme'");
    }

    @Test
    void rowFilterEmptyWhenNotConfigured() {
        assertThat(permissionService.getRowFilter("open-ds")).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Authentication auth(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private Map<String, Object> row(Object... kvPairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            map.put((String) kvPairs[i], kvPairs[i + 1]);
        }
        return map;
    }
}
