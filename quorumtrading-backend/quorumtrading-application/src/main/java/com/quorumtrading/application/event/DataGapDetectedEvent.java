package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Application event fired when the circuit breaker detects too many data gaps during simulation execution.
 *
 * <p>This event is fired when either:
 * <ul>
 *   <li>Consecutive skipped evaluations exceed the threshold (default: 5)</li>
 *   <li>Total skipped percentage exceeds the threshold (default: 50%)</li>
 * </ul>
 *
 * <p>The DataGapHandler will observe this event and:
 * <ol>
 *   <li>Wait for the configured delay (default: 60 seconds) to respect rate limits</li>
 *   <li>Analyze the remaining time range for gaps</li>
 *   <li>Trigger a new backfill request with the simulation linked</li>
 *   <li>Auto-resume the simulation when backfill completes</li>
 * </ol>
 *
 * @param simulationId The simulation that encountered data gaps
 * @param currentTimestamp The timestamp where the gap was detected
 * @param remainingEndTimestamp The end timestamp for the remaining simulation period (null for LIVE simulations with no end date)
 * @param missingSymbols The symbols that were missing data (may be partial)
 * @param consecutiveSkips Number of consecutive skipped evaluations
 * @param totalSkipPercentage Percentage of total evaluations that were skipped
 */
public record DataGapDetectedEvent(
    SimulationId simulationId,
    Instant currentTimestamp,
    Instant remainingEndTimestamp,
    Set<Symbol> missingSymbols,
    int consecutiveSkips,
    int totalSkipPercentage
) {
    public DataGapDetectedEvent {
        Objects.requireNonNull(simulationId, "simulationId cannot be null");
        Objects.requireNonNull(currentTimestamp, "currentTimestamp cannot be null");
        // remainingEndTimestamp may be null for LIVE simulations with no scheduled end date
        Objects.requireNonNull(missingSymbols, "missingSymbols cannot be null");
    }
}
