package com.unqueryservice.service;

import com.unqueryservice.config.QueryServiceProperties;
import com.unqueryservice.exception.PermissionDeniedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Row-level and field-level permission enforcement.
 *
 * <p>Permissions are configured per data-source under
 * {@code query-service.permissions.<datasource-name>}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final QueryServiceProperties properties;

    /**
     * Verifies that the authenticated user is allowed to query the given data source.
     *
     * @throws PermissionDeniedException if the user does not hold an allowed role
     */
    public void checkDataSourceAccess(String dataSourceName, Authentication authentication) {
        QueryServiceProperties.PermissionConfig perm =
                properties.getPermissions().get(dataSourceName);

        if (perm == null) {
            // No permission config means access is open to all authenticated users
            return;
        }

        List<String> allowedRoles = perm.getAllowedRoles();
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return;
        }

        Set<String> userRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean granted = allowedRoles.stream().anyMatch(userRoles::contains);
        if (!granted) {
            throw new PermissionDeniedException(
                    "User '" + authentication.getName() + "' is not authorised to query data source '" + dataSourceName + "'");
        }
    }

    /**
     * Applies field-level masking: replaces cell values in masked columns with
     * {@code "***"} for non-admin users.
     *
     * @param dataSourceName  the logical data source name
     * @param authentication  the current user's authentication context
     * @param rows            the raw rows returned by the query engine
     * @return                rows with sensitive columns masked as appropriate
     */
    public List<Map<String, Object>> applyFieldMasking(String dataSourceName,
                                                        Authentication authentication,
                                                        List<Map<String, Object>> rows) {
        QueryServiceProperties.PermissionConfig perm =
                properties.getPermissions().get(dataSourceName);

        if (perm == null || perm.getMaskedColumns() == null || perm.getMaskedColumns().isEmpty()) {
            return rows;
        }

        // Admins bypass masking
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(r -> r.equalsIgnoreCase("ROLE_ADMIN") || r.equalsIgnoreCase("ADMIN"));
        if (isAdmin) {
            return rows;
        }

        Set<String> maskedColumns = new HashSet<>(perm.getMaskedColumns());

        return rows.stream()
                .map(row -> {
                    Map<String, Object> masked = new LinkedHashMap<>(row);
                    maskedColumns.forEach(col -> {
                        if (masked.containsKey(col)) {
                            masked.put(col, "***");
                        }
                    });
                    return (Map<String, Object>) masked;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns the row-level SQL filter snippet for the given data source, or
     * an empty string if none is configured.
     */
    public String getRowFilter(String dataSourceName) {
        QueryServiceProperties.PermissionConfig perm =
                properties.getPermissions().get(dataSourceName);

        if (perm == null || perm.getRowFilter() == null || perm.getRowFilter().isBlank()) {
            return "";
        }
        return perm.getRowFilter();
    }
}
