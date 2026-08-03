package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message for error notifications.
 * Matches frontend type: ErrorMessage
 */
public class ErrorMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "ERROR";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("error")
    private String error;

    @JsonProperty("code")
    private String code;

    public ErrorMessage() {
    }

    public ErrorMessage(String timestamp, String simulationId, String error, String code) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.error = error;
        this.code = code;
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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
