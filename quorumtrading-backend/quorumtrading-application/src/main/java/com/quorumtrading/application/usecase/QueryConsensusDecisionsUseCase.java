package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.QueryConsensusDecisionsQuery;
import com.quorumtrading.model.entity.ConsensusDecision;

import java.util.List;

/**
 * Use case for retrieving consensus decisions for a simulation.
 */
public interface QueryConsensusDecisionsUseCase {

    /**
     * Execute the query consensus decisions use case.
     *
     * @param query the query containing simulation ID and filters
     * @return list of consensus decisions matching the query
     */
    List<ConsensusDecision> execute(QueryConsensusDecisionsQuery query);
}
