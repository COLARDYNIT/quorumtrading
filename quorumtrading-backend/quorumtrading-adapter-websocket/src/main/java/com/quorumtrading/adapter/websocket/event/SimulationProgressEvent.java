package com.quorumtrading.adapter.websocket.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when simulation status changes or progress updates.
 * Observed by MessageBroadcaster to broadcast to simulation.progress channel subscribers.
 */
public class SimulationProgressEvent {

    private final UUID simulationId;
    private final String status;
    private final Integer progress;
    private final String message;
    private final Instant timestamp;

    public SimulationProgressEvent(UUID simulationId, String status, Integer progress, String message, Instant timestamp) {
        this.simulationId = simulationId;
        this.status = status;
        this.progress = progress;
        this.message = message;
        this.timestamp = timestamp;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public String getStatus() {
        return status;
    }

    public Integer getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
