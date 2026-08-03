package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * WebSocket message for LLM agent decisions.
 * Matches frontend type: LLMDecisionMessage
 */
public class LLMDecisionMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "LLM_DECISION";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("decisionId")
    private long decisionId;

    @JsonProperty("agentType")
    private String agentType;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("suggestedAction")
    private String suggestedAction;

    @JsonProperty("confidence")
    private double confidence;

    @JsonProperty("indicatorSnapshot")
    private String indicatorSnapshot;

    @JsonProperty("uiPayload")
    private String uiPayload;

    public LLMDecisionMessage() {
    }

    public LLMDecisionMessage(String timestamp, String simulationId, long decisionId,
                              String agentType, String reasoning, String suggestedAction, double confidence,
                              String indicatorSnapshot, String uiPayload) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.decisionId = decisionId;
        this.agentType = agentType;
        this.reasoning = reasoning;
        this.suggestedAction = suggestedAction;
        this.confidence = confidence;
        this.indicatorSnapshot = indicatorSnapshot;
        this.uiPayload = uiPayload;
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

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getIndicatorSnapshot() {
        return indicatorSnapshot;
    }

    public void setIndicatorSnapshot(String indicatorSnapshot) {
        this.indicatorSnapshot = indicatorSnapshot;
    }

    public String getUiPayload() {
        return uiPayload;
    }

    public void setUiPayload(String uiPayload) {
        this.uiPayload = uiPayload;
    }
}
