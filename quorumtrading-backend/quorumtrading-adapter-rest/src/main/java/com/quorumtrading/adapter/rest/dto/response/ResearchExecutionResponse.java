package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.ResearchExecution;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for a single research execution.
 */
@Schema(description = "Research execution details")
public record ResearchExecutionResponse(
    @Schema(description = "Unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "Market research ID", example = "550e8400-e29b-41d4-a716-446655440001")
    UUID marketResearchId,

    @Schema(description = "Execution outcome", example = "STRATEGY_CREATED",
            enumeration = {"STRATEGY_CREATED", "NO_OPPORTUNITY", "FAILED"})
    String outcome,

    @Schema(description = "Researcher agent's conclusion and reasoning")
    String outcomeReasoning,

    @Schema(description = "Viability score (0-100, null if no opportunity found)", example = "85.5")
    BigDecimal viabilityScore,

    @Schema(description = "Created strategy ID (null if no strategy created)")
    UUID createdStrategyId,

    @Schema(description = "Simulation ID (null if auto-backtest not triggered)")
    UUID simulationId,

    @Schema(description = "Execution timestamp")
    Instant executedAt,

    @Schema(description = "Individual agent decisions")
    List<AgentDecisionResponse> agentDecisions,

    @Schema(description = "Billed cost in USD (raw LLM cost with the platform markup applied)", example = "0.00234500")
    BigDecimal billedCostUsd
) {
    /**
     * Nested DTO for agent decision.
     */
    @Schema(description = "Individual agent decision during research")
    public record AgentDecisionResponse(
        @Schema(description = "Unique identifier", example = "550e8400-e29b-41d4-a716-446655440002")
        UUID id,

        @Schema(description = "Agent type", example = "TECHNICAL",
                enumeration = {"TECHNICAL", "FUNDAMENTAL", "SENTIMENT"})
        String agentType,

        @Schema(description = "Agent's advice/conclusion")
        String advice,

        @Schema(description = "Agent's reasoning")
        String reasoning,

        @Schema(description = "Decision timestamp")
        Instant decidedAt,

        @Schema(description = "LLM model used for this decision", example = "openai/gpt-4o")
        String modelUsed,

        @Schema(description = "Number of prompt tokens", example = "500")
        Integer promptTokens,

        @Schema(description = "Number of completion tokens", example = "200")
        Integer completionTokens,

        @Schema(description = "Total tokens (prompt + completion)", example = "700")
        Integer totalTokens,

        @Schema(description = "Raw cost in USD (before markup)", example = "0.00350000")
        BigDecimal rawCostUsd,

        @Schema(description = "JSON array of tool calls made during agent reasoning")
        String toolCalls
    ) {}

    /**
     * Convert domain ResearchExecution entity to REST DTO.
     *
     * @param execution the domain entity
     * @return the response DTO
     */
    public static ResearchExecutionResponse fromDomain(ResearchExecution execution) {
        List<AgentDecisionResponse> agentDecisions = execution.getAgentDecisions().stream()
            .map(decision -> new AgentDecisionResponse(
                decision.getId(),
                decision.getAgentType().name(),
                decision.getAdvice(),
                decision.getReasoning(),
                decision.getDecidedAt(),
                decision.getModelUsed(),
                decision.getPromptTokens(),
                decision.getCompletionTokens(),
                decision.getTotalTokens(),
                decision.getRawCostUsd(),
                decision.getToolCalls()
            ))
            .toList();

        BigDecimal viabilityScore = null;
        if (execution.getViabilityScore() != null) {
            viabilityScore = execution.getViabilityScore().value();
        }

        BigDecimal billedCostUsd = execution.getBilledCost() != null
            ? execution.getBilledCost().amount()
            : null;

        return new ResearchExecutionResponse(
            execution.getId(),
            execution.getMarketResearchId(),
            execution.getOutcome().name(),
            execution.getResearcherConclusion(),
            viabilityScore,
            execution.getStrategyId(),
            execution.getSimulationId(),
            execution.getExecutedAt(),
            agentDecisions,
            billedCostUsd
        );
    }
}
