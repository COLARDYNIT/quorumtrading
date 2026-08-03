package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket message sent during research execution progress updates.
 * Represents real-time progress of market research execution including
 * agent start/completion notifications.
 * <p>
 * Note: This message does NOT implement BaseStreamMessage because research
 * executions are not tied to a specific simulation. They are independent
 * operations that may later create strategies for backtesting.
 * </p>
 * <p>
 * Stage values:
 * <ul>
 *     <li>STARTED - Research execution has begun</li>
 *     <li>AGENT_STARTED - A specific research agent has started its analysis</li>
 *     <li>AGENT_COMPLETED - A specific research agent has completed its analysis</li>
 *     <li>COMPLETED - Research execution has finished successfully</li>
 *     <li>FAILED - Research execution has failed</li>
 * </ul>
 * </p>
 */
@RegisterForReflection(methods = true, fields = true)
public class ResearchExecutionProgressMessage {

    @JsonProperty("type")
    private final String type;

    @JsonProperty("timestamp")
    private final String timestamp;

    @JsonProperty("researchId")
    private final String researchId;

    @JsonProperty("executionId")
    private final String executionId;

    @JsonProperty("stage")
    private final String stage;

    @JsonProperty("agentType")
    private final String agentType;

    @JsonProperty("durationMs")
    private final Long durationMs;

    @JsonProperty("outcome")
    private final String outcome;

    @JsonProperty("strategyId")
    private final String strategyId;

    @JsonProperty("errorMessage")
    private final String errorMessage;

    private ResearchExecutionProgressMessage(
            String type,
            String timestamp,
            String researchId,
            String executionId,
            String stage,
            String agentType,
            Long durationMs,
            String outcome,
            String strategyId,
            String errorMessage) {
        this.type = type;
        this.timestamp = timestamp;
        this.researchId = researchId;
        this.executionId = executionId;
        this.stage = stage;
        this.agentType = agentType;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.strategyId = strategyId;
        this.errorMessage = errorMessage;
    }

    /**
     * Factory method for research execution started message.
     */
    public static ResearchExecutionProgressMessage started(UUID researchId, UUID executionId, Instant timestamp) {
        return new ResearchExecutionProgressMessage(
            "RESEARCH_EXECUTION_PROGRESS",
            timestamp.toString(),
            researchId.toString(),
            executionId.toString(),
            "STARTED",
            null,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Factory method for agent started message.
     */
    public static ResearchExecutionProgressMessage agentStarted(
            UUID researchId,
            UUID executionId,
            String agentType,
            Instant timestamp) {
        return new ResearchExecutionProgressMessage(
            "RESEARCH_EXECUTION_PROGRESS",
            timestamp.toString(),
            researchId.toString(),
            executionId.toString(),
            "AGENT_STARTED",
            agentType,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Factory method for agent completed message.
     */
    public static ResearchExecutionProgressMessage agentCompleted(
            UUID researchId,
            UUID executionId,
            String agentType,
            long durationMs,
            Instant timestamp) {
        return new ResearchExecutionProgressMessage(
            "RESEARCH_EXECUTION_PROGRESS",
            timestamp.toString(),
            researchId.toString(),
            executionId.toString(),
            "AGENT_COMPLETED",
            agentType,
            durationMs,
            null,
            null,
            null
        );
    }

    /**
     * Factory method for research execution completed message.
     *
     * @param researchId  The research configuration ID
     * @param executionId The execution ID
     * @param outcome     The outcome (STRATEGY_CREATED, NO_OPPORTUNITY, or FAILED)
     * @param strategyId  The created strategy ID (nullable, only present if outcome is STRATEGY_CREATED)
     * @param timestamp   The completion timestamp
     */
    public static ResearchExecutionProgressMessage completed(
            UUID researchId,
            UUID executionId,
            String outcome,
            UUID strategyId,
            Instant timestamp) {
        return new ResearchExecutionProgressMessage(
            "RESEARCH_EXECUTION_PROGRESS",
            timestamp.toString(),
            researchId.toString(),
            executionId.toString(),
            "COMPLETED",
            null,
            null,
            outcome,
            strategyId != null ? strategyId.toString() : null,
            null
        );
    }

    /**
     * Factory method for research execution failed message.
     */
    public static ResearchExecutionProgressMessage failed(
            UUID researchId,
            UUID executionId,
            String errorMessage,
            Instant timestamp) {
        return new ResearchExecutionProgressMessage(
            "RESEARCH_EXECUTION_PROGRESS",
            timestamp.toString(),
            researchId.toString(),
            executionId.toString(),
            "FAILED",
            null,
            null,
            "FAILED",
            null,
            errorMessage
        );
    }

    // Getters
    public String getType() {
        return type;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getResearchId() {
        return researchId;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getStage() {
        return stage;
    }

    public String getAgentType() {
        return agentType;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
