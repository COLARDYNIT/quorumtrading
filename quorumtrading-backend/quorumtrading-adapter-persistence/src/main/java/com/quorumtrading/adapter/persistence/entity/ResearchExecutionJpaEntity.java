package com.quorumtrading.adapter.persistence.entity;

import com.quorumtrading.model.entity.ResearchAgentDecision;
import com.quorumtrading.model.entity.ResearchExecution;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ResearchOutcome;
import com.quorumtrading.model.valueobject.ViabilityScore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for ResearchExecution persistence.
 *
 * Maps to the research_executions table created by Flyway migration V34.
 * This is SEPARATE from the domain ResearchExecution to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 */
@Entity
@Table(name = "research_executions")
public class ResearchExecutionJpaEntity {

    /**
     * Primary key (UUID).
     */
    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Foreign key to market_research table.
     */
    @Column(name = "market_research_id", nullable = false)
    private UUID marketResearchId;

    /**
     * Many-to-one relationship to MarketResearchJpaEntity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_research_id", insertable = false, updatable = false)
    private MarketResearchJpaEntity marketResearch;

    /**
     * Result of the research execution (STRATEGY_CREATED, NO_OPPORTUNITY, or FAILED).
     */
    @Column(name = "outcome", length = 30, nullable = false)
    private String outcome;

    /**
     * Detailed explanation of why this outcome was reached.
     */
    @Column(name = "outcome_reasoning", columnDefinition = "TEXT", nullable = false)
    private String outcomeReasoning;

    /**
     * Viability score from 0-100 (null if outcome is not STRATEGY_CREATED).
     */
    @Column(name = "viability_score", precision = 5, scale = 2)
    private BigDecimal viabilityScore;

    /**
     * Foreign key to the created strategy (null if outcome is not STRATEGY_CREATED).
     */
    @Column(name = "created_strategy_id")
    private UUID createdStrategyId;

    /**
     * ID of the backtest simulation (null if auto_backtest was disabled or outcome is not STRATEGY_CREATED).
     */
    @Column(name = "simulation_id")
    private UUID simulationId;

    /**
     * Timestamp when the research execution completed.
     */
    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    /**
     * Agent decisions belonging to this research execution.
     * Cascade ALL operations, orphan removal enabled.
     */
    @OneToMany(mappedBy = "researchExecution", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ResearchAgentDecisionJpaEntity> agentDecisions = new ArrayList<>();

    // LLM cost tracking fields
    @Column(name = "prompt_tokens")
    private Integer promptTokens = 0;

    @Column(name = "completion_tokens")
    private Integer completionTokens = 0;

    @Column(name = "total_tokens")
    private Integer totalTokens = 0;

    @Column(name = "raw_cost_usd", precision = 18, scale = 8)
    private BigDecimal rawCostUsd = BigDecimal.ZERO;

    /**
     * Convert domain entity to JPA entity.
     */
    public static ResearchExecutionJpaEntity fromDomain(ResearchExecution domain) {
        var jpaEntity = new ResearchExecutionJpaEntity();

        jpaEntity.id = domain.getId();
        jpaEntity.marketResearchId = domain.getMarketResearchId();

        // Convert ResearchOutcome enum to string
        jpaEntity.outcome = domain.getOutcome().name();
        jpaEntity.outcomeReasoning = domain.getResearcherConclusion();

        // Convert ViabilityScore to BigDecimal (or null)
        if (domain.getViabilityScore() != null) {
            jpaEntity.viabilityScore = domain.getViabilityScore().value();
        }

        jpaEntity.createdStrategyId = domain.getStrategyId();
        jpaEntity.simulationId = domain.getSimulationId();
        jpaEntity.executedAt = domain.getExecutedAt();

        // Convert agent decisions from domain to JPA entities
        if (domain.getAgentDecisions() != null && !domain.getAgentDecisions().isEmpty()) {
            List<ResearchAgentDecisionJpaEntity> jpaAgentDecisions = domain.getAgentDecisions().stream()
                .map(ResearchAgentDecisionJpaEntity::fromDomain)
                .toList();
            // Set bidirectional relationship
            jpaAgentDecisions.forEach(decision -> decision.setResearchExecution(jpaEntity));
            jpaEntity.agentDecisions = new ArrayList<>(jpaAgentDecisions);
        }

        // Convert LLM usage for cost tracking
        LLMUsage usage = domain.getLLMUsage();
        if (usage != null) {
            jpaEntity.promptTokens = usage.promptTokens();
            jpaEntity.completionTokens = usage.completionTokens();
            jpaEntity.totalTokens = usage.totalTokens();
            jpaEntity.rawCostUsd = usage.rawCost();
        }

        return jpaEntity;
    }

    /**
     * Convert JPA entity to domain entity.
     */
    public ResearchExecution toDomain() {
        // Convert BigDecimal to ViabilityScore (or null)
        ViabilityScore viabilityScoreObj = null;
        if (viabilityScore != null) {
            viabilityScoreObj = new ViabilityScore(viabilityScore);
        }

        // Convert agent decisions from JPA to domain
        List<ResearchAgentDecision> domainAgentDecisions = agentDecisions.stream()
            .map(ResearchAgentDecisionJpaEntity::toDomain)
            .toList();

        // Convert LLM usage
        LLMUsage llmUsage = LLMUsage.of(
            promptTokens != null ? promptTokens : 0,
            completionTokens != null ? completionTokens : 0,
            totalTokens != null ? totalTokens : 0,
            rawCostUsd != null ? rawCostUsd : BigDecimal.ZERO
        );

        // Use reflection to reconstruct domain entity with all fields
        return new ResearchExecutionBuilder()
            .id(id)
            .marketResearchId(marketResearchId)
            .outcome(ResearchOutcome.valueOf(outcome))
            .researcherConclusion(outcomeReasoning)
            .viabilityScore(viabilityScoreObj)
            .strategyId(createdStrategyId)
            .simulationId(simulationId)
            .executedAt(executedAt)
            .agentDecisions(domainAgentDecisions)
            .llmUsage(llmUsage)
            .build();
    }

    /**
     * Builder to reconstruct domain entity with all fields.
     * Necessary because ResearchExecution has immutable fields set in constructor.
     */
    private static class ResearchExecutionBuilder {
        private UUID id;
        private UUID marketResearchId;
        private ResearchOutcome outcome;
        private String researcherConclusion;
        private ViabilityScore viabilityScore;
        private UUID strategyId;
        private UUID simulationId;
        private Instant executedAt;
        private List<ResearchAgentDecision> agentDecisions = new ArrayList<>();
        private LLMUsage llmUsage;

        ResearchExecutionBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        ResearchExecutionBuilder marketResearchId(UUID marketResearchId) {
            this.marketResearchId = marketResearchId;
            return this;
        }

        ResearchExecutionBuilder outcome(ResearchOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        ResearchExecutionBuilder researcherConclusion(String researcherConclusion) {
            this.researcherConclusion = researcherConclusion;
            return this;
        }

        ResearchExecutionBuilder viabilityScore(ViabilityScore viabilityScore) {
            this.viabilityScore = viabilityScore;
            return this;
        }

        ResearchExecutionBuilder strategyId(UUID strategyId) {
            this.strategyId = strategyId;
            return this;
        }

        ResearchExecutionBuilder simulationId(UUID simulationId) {
            this.simulationId = simulationId;
            return this;
        }

        ResearchExecutionBuilder executedAt(Instant executedAt) {
            this.executedAt = executedAt;
            return this;
        }

        ResearchExecutionBuilder agentDecisions(List<ResearchAgentDecision> agentDecisions) {
            this.agentDecisions = agentDecisions != null ? agentDecisions : new ArrayList<>();
            return this;
        }

        ResearchExecutionBuilder llmUsage(LLMUsage llmUsage) {
            this.llmUsage = llmUsage;
            return this;
        }

        ResearchExecution build() {
            try {
                // Create domain entity using constructor
                var domain = new ResearchExecution(
                    marketResearchId,
                    outcome,
                    researcherConclusion,
                    viabilityScore,
                    strategyId,
                    simulationId
                );

                // Use reflection to set immutable fields that differ from constructor defaults
                var idField = ResearchExecution.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(domain, id);

                var executedAtField = ResearchExecution.class.getDeclaredField("executedAt");
                executedAtField.setAccessible(true);
                executedAtField.set(domain, executedAt);

                // Use reflection to populate the agentDecisions list
                var agentDecisionsField = ResearchExecution.class.getDeclaredField("agentDecisions");
                agentDecisionsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<ResearchAgentDecision> domainList = (List<ResearchAgentDecision>) agentDecisionsField.get(domain);
                domainList.addAll(agentDecisions);

                // Set LLM usage
                if (llmUsage != null) {
                    domain.setLLMUsage(llmUsage);
                }

                return domain;
            } catch (Exception e) {
                throw new RuntimeException("Failed to reconstruct ResearchExecution domain entity", e);
            }
        }
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMarketResearchId() {
        return marketResearchId;
    }

    public void setMarketResearchId(UUID marketResearchId) {
        this.marketResearchId = marketResearchId;
    }

    public MarketResearchJpaEntity getMarketResearch() {
        return marketResearch;
    }

    public void setMarketResearch(MarketResearchJpaEntity marketResearch) {
        this.marketResearch = marketResearch;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getOutcomeReasoning() {
        return outcomeReasoning;
    }

    public void setOutcomeReasoning(String outcomeReasoning) {
        this.outcomeReasoning = outcomeReasoning;
    }

    public BigDecimal getViabilityScore() {
        return viabilityScore;
    }

    public void setViabilityScore(BigDecimal viabilityScore) {
        this.viabilityScore = viabilityScore;
    }

    public UUID getCreatedStrategyId() {
        return createdStrategyId;
    }

    public void setCreatedStrategyId(UUID createdStrategyId) {
        this.createdStrategyId = createdStrategyId;
    }

    public UUID getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(UUID simulationId) {
        this.simulationId = simulationId;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public List<ResearchAgentDecisionJpaEntity> getAgentDecisions() {
        return agentDecisions;
    }

    public void setAgentDecisions(List<ResearchAgentDecisionJpaEntity> agentDecisions) {
        this.agentDecisions = agentDecisions;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getRawCostUsd() {
        return rawCostUsd;
    }

    public void setRawCostUsd(BigDecimal rawCostUsd) {
        this.rawCostUsd = rawCostUsd;
    }
}
