package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message sent when simulation completes successfully.
 * Matches frontend type: SimulationCompletedMessage
 */
public class SimulationCompletedMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "SIMULATION_COMPLETED";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("finalCapital")
    private double finalCapital;

    @JsonProperty("returnPercentage")
    private double returnPercentage;

    @JsonProperty("totalTrades")
    private int totalTrades;

    @JsonProperty("duration")
    private long duration; // milliseconds

    public SimulationCompletedMessage() {
    }

    public SimulationCompletedMessage(String timestamp, String simulationId, double finalCapital,
                                      double returnPercentage, int totalTrades, long duration) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.finalCapital = finalCapital;
        this.returnPercentage = returnPercentage;
        this.totalTrades = totalTrades;
        this.duration = duration;
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

    public double getFinalCapital() {
        return finalCapital;
    }

    public void setFinalCapital(double finalCapital) {
        this.finalCapital = finalCapital;
    }

    public double getReturnPercentage() {
        return returnPercentage;
    }

    public void setReturnPercentage(double returnPercentage) {
        this.returnPercentage = returnPercentage;
    }

    public int getTotalTrades() {
        return totalTrades;
    }

    public void setTotalTrades(int totalTrades) {
        this.totalTrades = totalTrades;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
}
