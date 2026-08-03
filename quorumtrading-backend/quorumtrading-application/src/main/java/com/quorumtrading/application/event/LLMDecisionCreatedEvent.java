package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;

/**
 * Domain event fired when an LLM decision is created and saved.
 * This event is observed by adapters (e.g., WebSocket adapter) to broadcast updates.
 */
public class LLMDecisionCreatedEvent {

    private final SimulationId simulationId;
    private final Long decisionId;
    private final String agentRole;
    private final String recommendation;
    private final String reasoning;
    private final String suggestedAction;
    private final double confidence;
    private final Instant timestamp;
    private final String indicatorSnapshot;
    private final String uiPayload;

    public LLMDecisionCreatedEvent(
            SimulationId simulationId,
            Long decisionId,
            String agentRole,
            String recommendation,
            String reasoning,
            String suggestedAction,
            double confidence,
            Instant timestamp,
            String indicatorSnapshot,
            String uiPayload) {
        this.simulationId = simulationId;
        this.decisionId = decisionId;
        this.agentRole = agentRole;
        this.recommendation = recommendation;
        this.reasoning = reasoning;
        this.suggestedAction = suggestedAction;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.indicatorSnapshot = indicatorSnapshot;
        this.uiPayload = uiPayload;
    }

    public SimulationId getSimulationId() {
        return simulationId;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public String getAgentRole() {
        return agentRole;
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
