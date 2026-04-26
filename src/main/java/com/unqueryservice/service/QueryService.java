package com.unqueryservice.service;

import com.unqueryservice.model.PageResult;
import com.unqueryservice.model.QueryRequest;
import com.unqueryservice.model.QueryResult;

/**
 * High-level query service contract.
 *
 * <p>Returns either a {@link QueryResult} (non-paged) or a {@link PageResult} (paged),
 * depending on whether {@code page} and {@code pageSize} are set in the request.
 */
public interface QueryService {

    /**
     * Executes a non-paged query.
     *
     * @param request the validated query request (page/pageSize absent)
     * @return        query result
     */
    QueryResult execute(QueryRequest request);

    /**
     * Executes a paginated query: first runs COUNT(*), then fetches the slice.
     *
     * @param request the validated query request (page and pageSize present)
     * @return        paged result
     */
    PageResult executePaged(QueryRequest request);
}
