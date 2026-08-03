package com.quorumtrading.adapter.websocket.event;

import java.time.Instant;
import java.util.UUID;

/**
 * CDI event fired when an LLM agent makes a decision.
 * Observed by MessageBroadcaster to broadcast to agent.decisions channel subscribers.
 */
public class AgentDecisionEvent {

    private final UUID simulationId;
    private final Long decisionId;
    private final String agentId;
    private final String recommendation;
    private final String reasoning;
    private final String suggestedAction;
    private final double confidence;
    private final Instant timestamp;
    private final String indicatorSnapshot;
    private final String uiPayload;

    public AgentDecisionEvent(UUID simulationId, Long decisionId, String agentId, String recommendation,
                              String reasoning, String suggestedAction, double confidence, Instant timestamp,
                              String indicatorSnapshot, String uiPayload) {
        this.simulationId = simulationId;
        this.decisionId = decisionId;
        this.agentId = agentId;
        this.recommendation = recommendation;
        this.reasoning = reasoning;
        this.suggestedAction = suggestedAction;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.indicatorSnapshot = indicatorSnapshot;
        this.uiPayload = uiPayload;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public double getConfidence() {
        return confidence;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getIndicatorSnapshot() {
        return indicatorSnapshot;
    }

    public String getUiPayload() {
        return uiPayload;
    }
}
