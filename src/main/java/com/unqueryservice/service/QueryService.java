package com.unqueryservice.service;

import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;
import org.springframework.security.core.Authentication;

/**
 * High-level query service contract.
 */
public interface QueryService {

    /**
     * Executes the given query request on behalf of the authenticated user.
     *
     * @param request        the validated query request
     * @param authentication the current user's authentication context
     * @return               the query result (possibly from cache)
     */
    QueryResult execute(QueryRequest request, Authentication authentication);
}
