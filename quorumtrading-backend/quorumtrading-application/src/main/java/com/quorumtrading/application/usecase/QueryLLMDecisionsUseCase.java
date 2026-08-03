package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.QueryLLMDecisionsQuery;
import com.quorumtrading.model.entity.LLMDecision;

import java.util.List;

/**
 * Use case for retrieving LLM decisions for a simulation.
 */
public interface QueryLLMDecisionsUseCase {

    /**
     * Execute the query LLM decisions use case.
     *
     * @param query the query containing simulation ID and filters
     * @return list of LLM decisions matching the query
     */
    List<LLMDecision> execute(QueryLLMDecisionsQuery query);
}
