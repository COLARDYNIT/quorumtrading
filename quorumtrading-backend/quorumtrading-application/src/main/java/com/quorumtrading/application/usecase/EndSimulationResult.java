package com.quorumtrading.application.usecase;

import java.util.List;

/**
 * Result of processing simulation end dates.
 *
 * @param simulationsEnded    Number of LIVE simulations whose period elapsed and were completed
 * @param simulationsSkipped  Number of simulations skipped (already completed/failed, not started)
 * @param simulationsFailed   Number of simulations that failed while being processed
 * @param simulationsPaused   Number of stalled backtests paused with a reason. Deliberately its
 *                            own counter and NOT folded into {@code simulationsEnded}: a pause is
 *                            not an ending, and reporting it as one is how the watchdog this
 *                            counter belongs to used to lie (ADR-0004).
 * @param errors              List of error messages
 */
public record EndSimulationResult(
        int simulationsEnded,
        int simulationsSkipped,
        int simulationsFailed,
        int simulationsPaused,
        List<String> errors
) {
    /** Convenience for callers with nothing paused (keeps the pre-detector arity readable). */
    public EndSimulationResult(int simulationsEnded, int simulationsSkipped,
                               int simulationsFailed, List<String> errors) {
        this(simulationsEnded, simulationsSkipped, simulationsFailed, 0, errors);
    }
}
