package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message for trade execution updates.
 * Sent when a consensus decision is executed as a trade.
 */
public class TradeExecutionMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "TRADE_EXECUTION";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("consensusDecisionId")
    private Long consensusDecisionId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("quantity")
    private double quantity;

    @JsonProperty("executionPrice")
    private double executionPrice;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("portfolioSnapshotId")
    private Long portfolioSnapshotId;

    public TradeExecutionMessage() {
    }

    public TradeExecutionMessage(
            String timestamp,
            String simulationId,
            Long consensusDecisionId,
            String action,
            String symbol,
            double quantity,
            double executionPrice,
            boolean success,
            String message,
            Long portfolioSnapshotId) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.consensusDecisionId = consensusDecisionId;
        this.action = action;
        this.symbol = symbol;
        this.quantity = quantity;
        this.executionPrice = executionPrice;
        this.success = success;
        this.message = message;
        this.portfolioSnapshotId = portfolioSnapshotId;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public Long getConsensusDecisionId() {
        return consensusDecisionId;
    }

    public void setConsensusDecisionId(Long consensusDecisionId) {
        this.consensusDecisionId = consensusDecisionId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(double executionPrice) {
        this.executionPrice = executionPrice;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getPortfolioSnapshotId() {
        return portfolioSnapshotId;
    }

    public void setPortfolioSnapshotId(Long portfolioSnapshotId) {
        this.portfolioSnapshotId = portfolioSnapshotId;
    }
}
