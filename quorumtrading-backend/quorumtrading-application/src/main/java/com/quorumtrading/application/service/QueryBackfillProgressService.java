package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BackfillJobRepository;
import com.quorumtrading.application.usecase.QueryBackfillProgressUseCase;
import com.quorumtrading.application.usecase.query.QueryBackfillProgressQuery;
import com.quorumtrading.model.marketdata.BackfillProgress;

/**
 * Service implementation for querying backfill job progress.
 *
 * <p>This service:</p>
 * <ul>
 *   <li>Retrieves backfill request from repository by ID</li>
 *   <li>Converts BackfillJob entity to BackfillProgress value object</li>
 *   <li>Returns null if job not found (not an error - client should handle 404)</li>
 * </ul>
 */
public class QueryBackfillProgressService implements QueryBackfillProgressUseCase {

    private final BackfillJobRepository repository;

    public QueryBackfillProgressService(BackfillJobRepository repository) {
        this.repository = repository;
    }

    @Override
    public BackfillProgress execute(QueryBackfillProgressQuery query) {
        return repository.findById(query.jobId())
            .map(BackfillProgress::from)
            .orElse(null);
    }
}
