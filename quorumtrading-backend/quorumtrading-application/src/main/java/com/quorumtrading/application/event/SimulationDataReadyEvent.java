package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;
import java.util.Objects;

/**
 * Application event fired when simulation data is ready.
 *
 * <p>For LIVE simulations: fired immediately (no backfill needed).</p>
 * <p>For BACKTEST simulations: fired after backfill completes or if no gaps exist.</p>
 *
 * <p><b>CRITICAL:</b> Must carry simulationId to ensure only the correct
 * simulation transitions to SIMULATING state.</p>
 */
public record SimulationDataReadyEvent(SimulationId simulationId) {
    public SimulationDataReadyEvent {
        Objects.requireNonNull(simulationId, "simulationId cannot be null");
    }
}
