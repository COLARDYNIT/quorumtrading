package com.quorumtrading.application.usecase;

import java.util.List;

/**
 * Result of evaluating portfolios for all active simulations.
 *
 * @param processed number of simulations successfully evaluated
 * @param skipped number of simulations skipped (no price changes or conditions not met)
 * @param failed number of simulations that failed evaluation
 * @param errors list of error messages for failed evaluations
 */
public record PortfolioEvaluationResult(
        int processed,
        int skipped,
        int failed,
        List<String> errors
) {
}
