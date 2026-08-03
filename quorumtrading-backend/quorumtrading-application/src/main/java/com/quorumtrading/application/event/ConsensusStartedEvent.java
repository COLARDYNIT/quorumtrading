package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;

/**
 * Domain event fired when consensus processing begins.
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast processing indicators.
 */
public class ConsensusStartedEvent {

    private final SimulationId simulationId;
    private final String consensusRequestId;
    private final Instant decisionTimestamp;

    public ConsensusStartedEvent(
            SimulationId simulationId,
            String consensusRequestId,
            Instant decisionTimestamp) {
        this.simulationId = simulationId;
        this.consensusRequestId = consensusRequestId;
        this.decisionTimestamp = decisionTimestamp;
    }

    public SimulationId getSimulationId() {
        return simulationId;
    }

    public String getConsensusRequestId() {
        return consensusRequestId;
    }

    public Instant getDecisionTimestamp() {
        return decisionTimestamp;
    }
}
