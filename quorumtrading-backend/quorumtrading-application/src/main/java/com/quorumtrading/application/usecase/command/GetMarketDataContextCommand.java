package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;

/**
 * Command to retrieve market data context for a simulation at a specific timestamp.
 *
 * @param simulationId     The simulation ID
 * @param analysisTimestamp The timestamp to retrieve market data for (same as used during consensus)
 * @param requestingUserId The user requesting the data (null for system/internal calls)
 */
public record GetMarketDataContextCommand(
    SimulationId simulationId,
    Instant analysisTimestamp,
    UserId requestingUserId
) {
    /**
     * Backward-compatible constructor without userId.
     */
    public GetMarketDataContextCommand(SimulationId simulationId, Instant analysisTimestamp) {
        this(simulationId, analysisTimestamp, null);
    }
}
