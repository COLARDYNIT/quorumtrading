package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message for consensus decisions.
 * Matches frontend type: ConsensusDecisionMessage
 */
public class ConsensusDecisionMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "CONSENSUS_DECISION";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("decisionId")
    private long decisionId;

    @JsonProperty("finalAction")
    private String finalAction;

    @JsonProperty("executed")
    private boolean executed;

    @JsonProperty("executionPrice")
    private Double executionPrice;

    public ConsensusDecisionMessage() {
    }

    public ConsensusDecisionMessage(String timestamp, String simulationId, long decisionId,
                                    String finalAction, boolean executed, Double executionPrice) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.decisionId = decisionId;
        this.finalAction = finalAction;
        this.executed = executed;
        this.executionPrice = executionPrice;
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

    public long getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(long decisionId) {
        this.decisionId = decisionId;
    }

    public String getFinalAction() {
        return finalAction;
    }

    public void setFinalAction(String finalAction) {
        this.finalAction = finalAction;
    }

    public boolean isExecuted() {
        return executed;
    }

    public void setExecuted(boolean executed) {
        this.executed = executed;
    }

    public Double getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(Double executionPrice) {
        this.executionPrice = executionPrice;
    }
}
