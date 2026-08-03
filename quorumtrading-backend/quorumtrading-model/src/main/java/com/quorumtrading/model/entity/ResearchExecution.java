package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import com.quorumtrading.model.valueobject.ViabilityScore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing a single market research execution.
 * Child entity of MarketResearch aggregate.
 */
public class ResearchExecution {

    private final UUID id;
    private final UUID marketResearchId;
    private final ResearchOutcome outcome;
    private final String researcherConclusion;
    private final ViabilityScore viabilityScore;
    private final UUID strategyId;
    private UUID simulationId;
    private final Instant executedAt;
    private final List<ResearchAgentDecision> agentDecisions;

    // LLM cost tracking
    private LLMUsage llmUsage;

    /**
     * Create a new research execution with auto-generated ID.
     */
    public ResearchExecution(
        UUID marketResearchId,
        ResearchOutcome outcome,
        String researcherConclusion,
        ViabilityScore viabilityScore,
        UUID strategyId,
        UUID simulationId
    ) {
        this(UUID.randomUUID(), marketResearchId, outcome, researcherConclusion, viabilityScore, strategyId, simulationId);
    }

    /**
     * Create a new research execution with a pre-generated ID.
     * <p>
     * This constructor is used when the execution ID needs to be known upfront,
     * such as for async execution where the ID is returned immediately before
     * execution completes.
     * </p>
     *
     * @param id                   The pre-generated execution ID
     * @param marketResearchId     The parent market research ID
     * @param outcome              The research outcome
     * @param researcherConclusion The researcher's conclusion
     * @param viabilityScore       The viability score (may be null)
     * @param strategyId           The created strategy ID (may be null)
     * @param simulationId         The backtest simulation ID (may be null)
     */
    public ResearchExecution(
        UUID id,
        UUID marketResearchId,
        ResearchOutcome outcome,
        String researcherConclusion,
        ViabilityScore viabilityScore,
        UUID strategyId,
        UUID simulationId
    ) {
        if (id == null) {
            throw new IllegalArgumentException("Execution ID cannot be null");
        }
        if (marketResearchId == null) {
            throw new IllegalArgumentException("Market research ID cannot be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Outcome cannot be null");
        }
        if (researcherConclusion == null || researcherConclusion.isBlank()) {
            throw new IllegalArgumentException("Researcher conclusion cannot be blank");
        }

        this.id = id;
        this.marketResearchId = marketResearchId;
        this.outcome = outcome;
        this.researcherConclusion = researcherConclusion;
        this.viabilityScore = viabilityScore;
        this.strategyId = strategyId;
        this.simulationId = simulationId;
        this.executedAt = Instant.now();
        this.agentDecisions = new ArrayList<>();
    }

    /**
     * Add an agent decision to this execution (without LLM usage tracking).
     */
    public void addAgentDecision(ResearchAgentType agentType, String advice, String reasoning) {
        addAgentDecision(agentType, advice, reasoning, null);
    }

    /**
     * Add an agent decision to this execution with LLM usage tracking.
     */
    public void addAgentDecision(ResearchAgentType agentType, String advice, String reasoning, LLMUsage llmUsage) {
        addAgentDecision(agentType, advice, reasoning, llmUsage, null);
    }

    /**
     * Add an agent decision to this execution with LLM usage tracking and tool calls.
     */
    public void addAgentDecision(ResearchAgentType agentType, String advice, String reasoning, LLMUsage llmUsage, String toolCalls) {
        ResearchAgentDecision decision = new ResearchAgentDecision(this.id, agentType, advice, reasoning, llmUsage, toolCalls);
        this.agentDecisions.add(decision);
    }

    /**
     * Set the simulation ID (called when auto-backtest is triggered).
     */
    public void setSimulationId(UUID simulationId) {
        this.simulationId = simulationId;
    }

    /**
     * Add LLM usage to this execution (accumulates with existing usage).
     */
    public void addLLMUsage(LLMUsage usage) {
        if (usage == null) {
            return;
        }
        if (this.llmUsage == null) {
            this.llmUsage = usage;
        } else {
            this.llmUsage = this.llmUsage.add(usage);
        }
    }

    /**
     * Set the LLM usage for this execution (replaces existing).
     */
    public void setLLMUsage(LLMUsage usage) {
        this.llmUsage = usage;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getMarketResearchId() { return marketResearchId; }
    public ResearchOutcome getOutcome() { return outcome; }
    public String getResearcherConclusion() { return researcherConclusion; }
    public ViabilityScore getViabilityScore() { return viabilityScore; }
    public UUID getStrategyId() { return strategyId; }
    public UUID getSimulationId() { return simulationId; }
    public Instant getExecutedAt() { return executedAt; }
    public List<ResearchAgentDecision> getAgentDecisions() {
        return Collections.unmodifiableList(agentDecisions);
    }

    public LLMUsage getLLMUsage() {
        return llmUsage != null ? llmUsage : LLMUsage.ZERO;
    }

    /**
     * Get the billed cost for this execution (raw cost with the platform markup applied).
     */
    public Money getBilledCost() {
        return getLLMUsage().billedCostAsMoney();
    }

    /**
     * Get the raw LLM cost for this execution.
     */
    public Money getRawCost() {
        return getLLMUsage().rawCostAsMoney();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResearchExecution that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
