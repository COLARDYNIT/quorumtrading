package com.quorumtrading.adapter.websocket.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when a consensus decision is executed as a trade.
 * Observed by MessageBroadcaster to broadcast to trade.executions channel subscribers.
 */
public class TradeExecutionEvent {

    private final UUID simulationId;
    private final Long consensusDecisionId;
    private final String action;
    private final String symbol;
    private final BigDecimal quantity;
    private final BigDecimal executionPrice;
    private final boolean success;
    private final String message;
    private final Long portfolioSnapshotId;
    private final Instant timestamp;

    public TradeExecutionEvent(
            UUID simulationId,
            Long consensusDecisionId,
            String action,
            String symbol,
            BigDecimal quantity,
            BigDecimal executionPrice,
            boolean success,
            String message,
            Long portfolioSnapshotId,
            Instant timestamp) {
        this.simulationId = simulationId;
        this.consensusDecisionId = consensusDecisionId;
        this.action = action;
        this.symbol = symbol;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.success = success;
        this.message = message;
        this.portfolioSnapshotId = portfolioSnapshotId;
        this.timestamp = timestamp;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public Long getConsensusDecisionId() {
        return consensusDecisionId;
    }

    public String getAction() {
        return action;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Long getPortfolioSnapshotId() {
        return portfolioSnapshotId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
