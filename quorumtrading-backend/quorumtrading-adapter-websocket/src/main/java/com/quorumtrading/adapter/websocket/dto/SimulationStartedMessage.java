package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message sent when simulation starts.
 * Matches frontend type: SimulationStartedMessage
 */
public class SimulationStartedMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "SIMULATION_STARTED";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("strategyId")
    private String strategyId;

    @JsonProperty("initialCapital")
    private double initialCapital;

    public SimulationStartedMessage() {
    }

    public SimulationStartedMessage(String timestamp, String simulationId, String strategyId, double initialCapital) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.strategyId = strategyId;
        this.initialCapital = initialCapital;
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

    public String getStrategyId() {
        return strategyId;
    }

    public void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    public double getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(double initialCapital) {
        this.initialCapital = initialCapital;
    }
}
