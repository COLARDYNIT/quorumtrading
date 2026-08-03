package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Application event fired when a consensus decision is executed as a trade.
 */
public class TradeExecutionEvent {

    private final SimulationId simulationId;
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
            SimulationId simulationId,
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

    public SimulationId getSimulationId() {
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
