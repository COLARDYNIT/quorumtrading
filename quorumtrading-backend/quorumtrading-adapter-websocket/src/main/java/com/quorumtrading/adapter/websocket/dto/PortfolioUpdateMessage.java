package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * WebSocket message for portfolio updates.
 * Matches frontend type: PortfolioUpdateMessage
 */
public class PortfolioUpdateMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "PORTFOLIO_UPDATE";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("cashBalance")
    private double cashBalance;

    @JsonProperty("positions")
    private Map<String, Object> positions;

    @JsonProperty("totalValue")
    private double totalValue;

    public PortfolioUpdateMessage() {
    }

    public PortfolioUpdateMessage(String timestamp, String simulationId, double cashBalance,
                                  Map<String, Object> positions, double totalValue) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.cashBalance = cashBalance;
        this.positions = positions;
        this.totalValue = totalValue;
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

    public double getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(double cashBalance) {
        this.cashBalance = cashBalance;
    }

    public Map<String, Object> getPositions() {
        return positions;
    }

    public void setPositions(Map<String, Object> positions) {
        this.positions = positions;
    }

    public double getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(double totalValue) {
        this.totalValue = totalValue;
    }
}
