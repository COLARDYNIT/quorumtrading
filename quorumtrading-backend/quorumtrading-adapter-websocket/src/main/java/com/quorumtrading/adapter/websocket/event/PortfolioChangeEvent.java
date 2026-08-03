package com.quorumtrading.adapter.websocket.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when portfolio snapshot is created.
 * Observed by MessageBroadcaster to broadcast to portfolio.changes channel subscribers.
 */
public class PortfolioChangeEvent {

    private final UUID simulationId;
    private final BigDecimal totalValueUsd;
    private final BigDecimal cashAvailable;
    private final Instant timestamp;
    private final String positions; // JSON representation of positions

    public PortfolioChangeEvent(UUID simulationId, BigDecimal totalValueUsd, BigDecimal cashAvailable,
                               Instant timestamp, String positions) {
        this.simulationId = simulationId;
        this.totalValueUsd = totalValueUsd;
        this.cashAvailable = cashAvailable;
        this.timestamp = timestamp;
        this.positions = positions;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public BigDecimal getTotalValueUsd() {
        return totalValueUsd;
    }

    public BigDecimal getCashAvailable() {
        return cashAvailable;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getPositions() {
        return positions;
    }
}
