package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message sent when simulation fails.
 * Matches frontend type: SimulationFailedMessage
 */
public class SimulationFailedMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "SIMULATION_FAILED";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("error")
    private String error;

    @JsonProperty("stackTrace")
    private String stackTrace;

    public SimulationFailedMessage() {
    }

    public SimulationFailedMessage(String timestamp, String simulationId, String error, String stackTrace) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.error = error;
        this.stackTrace = stackTrace;
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

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }
}
