package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.QueryPortfolioSnapshotsQuery;
import com.quorumtrading.model.entity.PortfolioSnapshot;

import java.util.List;

/**
 * Use case for retrieving portfolio snapshots for a simulation.
 */
public interface QueryPortfolioSnapshotsUseCase {

    /**
     * Execute the query portfolio snapshots use case.
     *
     * @param query the query containing simulation ID and filters
     * @return list of portfolio snapshots matching the query
     */
    List<PortfolioSnapshot> execute(QueryPortfolioSnapshotsQuery query);
}
