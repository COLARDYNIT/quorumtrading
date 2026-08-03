package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.QueryBackfillProgressQuery;
import com.quorumtrading.model.marketdata.BackfillProgress;

/**
 * Use case for querying the progress of a backfill job.
 * Part of the query-side in a CQRS-lite architecture.
 */
public interface QueryBackfillProgressUseCase {
    /**
     * Query the progress of a backfill job.
     *
     * @param query the query containing job ID
     * @return backfill progress, or null if job not found
     */
    BackfillProgress execute(QueryBackfillProgressQuery query);
}
