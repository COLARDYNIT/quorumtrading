package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message sent during simulation progress updates.
 * Matches frontend type: SimulationProgressMessage
 */
public class SimulationProgressMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "SIMULATION_PROGRESS";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("progress")
    private double progress; // 0-100

    @JsonProperty("currentTimestamp")
    private String currentTimestamp;

    @JsonProperty("eventsProcessed")
    private int eventsProcessed;

    public SimulationProgressMessage() {
    }

    public SimulationProgressMessage(String timestamp, String simulationId, double progress,
                                     String currentTimestamp, int eventsProcessed) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.progress = progress;
        this.currentTimestamp = currentTimestamp;
        this.eventsProcessed = eventsProcessed;
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

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public String getCurrentTimestamp() {
        return currentTimestamp;
    }

    public void setCurrentTimestamp(String currentTimestamp) {
        this.currentTimestamp = currentTimestamp;
    }

    public int getEventsProcessed() {
        return eventsProcessed;
    }

    public void setEventsProcessed(int eventsProcessed) {
        this.eventsProcessed = eventsProcessed;
    }
}
