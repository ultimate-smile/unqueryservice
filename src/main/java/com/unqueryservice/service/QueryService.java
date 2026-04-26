package com.unqueryservice.service;

import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;

/**
 * High-level query service contract.
 *
 * <p>Authentication and permission control are delegated to ThingsBoard.
 * This service assumes every inbound request is already authorised.
 */
public interface QueryService {

    /**
     * Executes the given query request.
     *
     * @param request the validated query request
     * @return        the query result (possibly from cache)
     */
    QueryResult execute(QueryRequest request);
}
