package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain event fired when a consensus round is skipped (e.g., no market data available).
 * This event notifies the frontend to clear the "in progress" state.
 */
public record ConsensusSkippedEvent(
    SimulationId simulationId,
    String consensusRequestId,
    Instant decisionTimestamp,
    String reason
) {
    public ConsensusSkippedEvent {
        Objects.requireNonNull(simulationId, "simulationId cannot be null");
        Objects.requireNonNull(consensusRequestId, "consensusRequestId cannot be null");
        Objects.requireNonNull(decisionTimestamp, "decisionTimestamp cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
