package com.quorumtrading.adapter.websocket.event;

import java.math.BigDecimal;

/**
 * Event representing portfolio value updates during simulation.
 * Broadcasted via WebSocket to provide real-time portfolio tracking.
 *
 * @param simulationId  The UUID of the simulation
 * @param totalValue    Total portfolio value (positions + cash)
 * @param cashAvailable Available cash for trading
 */
public record PortfolioUpdateEvent(
    String simulationId,
    BigDecimal totalValue,
    BigDecimal cashAvailable
) {
    /**
     * Creates a portfolio update event.
     */
    public static PortfolioUpdateEvent of(String simulationId, BigDecimal totalValue, BigDecimal cashAvailable) {
        return new PortfolioUpdateEvent(simulationId, totalValue, cashAvailable);
    }
}
