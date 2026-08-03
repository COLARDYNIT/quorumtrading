package com.quorumtrading.application.usecase;

import java.util.List;

/**
 * Result of processing consensus for all active simulations.
 *
 * @param simulationsProcessed Number of simulations successfully processed
 * @param simulationsSkipped   Number of simulations skipped (no data, etc.)
 * @param simulationsFailed    Number of simulations that failed processing
 * @param errors               List of error messages
 */
public record ScheduledConsensusResult(
        int simulationsProcessed,
        int simulationsSkipped,
        int simulationsFailed,
        List<String> errors
) {
}
