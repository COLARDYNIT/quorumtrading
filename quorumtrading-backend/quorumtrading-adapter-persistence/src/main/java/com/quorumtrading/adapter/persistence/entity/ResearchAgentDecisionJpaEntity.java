package com.quorumtrading.adapter.persistence.entity;

import com.quorumtrading.model.entity.ResearchAgentDecision;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for ResearchAgentDecision persistence.
 *
 * Maps to the research_agent_decisions table created by Flyway migration V35.
 * This is SEPARATE from the domain ResearchAgentDecision to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 */
@Entity
@Table(name = "research_agent_decisions")
public class ResearchAgentDecisionJpaEntity {

    /**
     * Primary key (UUID).
     */
    @Id
    @Column(name = "id")
    private UUID id;

    /**
     * Foreign key to research_executions table.
     */
    @Column(name = "research_execution_id", nullable = false)
    private UUID researchExecutionId;

    /**
     * Many-to-one relationship to ResearchExecutionJpaEntity.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "research_execution_id", insertable = false, updatable = false)
    private ResearchExecutionJpaEntity researchExecution;

    /**
     * Type of agent that made this decision (NEWS_SENTIMENT_ANALYST, STRATEGY_DESIGNER, or VIABILITY_ASSESSOR).
     */
    @Column(name = "agent_type", length = 50, nullable = false)
    private String agentType;

    /**
     * The agent's advice or recommendation.
     */
    @Column(name = "advice", columnDefinition = "TEXT", nullable = false)
    private String advice;

    /**
     * The agent's reasoning and analysis supporting the advice.
     */
    @Column(name = "reasoning", columnDefinition = "TEXT", nullable = false)
    private String reasoning;

    /**
     * Timestamp when the agent made this decision.
     */
    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    // LLM usage/cost tracking fields

    /**
     * The model used for this agent decision (e.g., "openai/gpt-4o").
     */
    @Column(name = "model_used", length = 100)
    private String modelUsed;

    /**
     * Number of prompt tokens used.
     */
    @Column(name = "prompt_tokens")
    private int promptTokens;

    /**
     * Number of completion tokens generated.
     */
    @Column(name = "completion_tokens")
    private int completionTokens;

    /**
     * Total tokens (prompt + completion).
     */
    @Column(name = "total_tokens")
    private int totalTokens;

    /**
     * Raw cost in USD from OpenRouter (before markup).
     */
    @Column(name = "raw_cost_usd", precision = 18, scale = 8)
    private BigDecimal rawCostUsd;

    /**
     * JSON array of tool calls made during agent reasoning.
     */
    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;

    /**
     * Convert domain entity to JPA entity.
     */
    public static ResearchAgentDecisionJpaEntity fromDomain(ResearchAgentDecision domain) {
        var jpaEntity = new ResearchAgentDecisionJpaEntity();

        jpaEntity.id = domain.getId();
        jpaEntity.researchExecutionId = domain.getResearchExecutionId();

        // Convert ResearchAgentType enum to string
        jpaEntity.agentType = domain.getAgentType().name();

        jpaEntity.advice = domain.getAdvice();
        jpaEntity.reasoning = domain.getReasoning();
        jpaEntity.decidedAt = domain.getDecidedAt();

        // LLM cost tracking
        jpaEntity.modelUsed = domain.getModelUsed();
        jpaEntity.promptTokens = domain.getPromptTokens();
        jpaEntity.completionTokens = domain.getCompletionTokens();
        jpaEntity.totalTokens = domain.getTotalTokens();
        jpaEntity.rawCostUsd = domain.getRawCostUsd();
        jpaEntity.toolCalls = domain.getToolCalls();

        return jpaEntity;
    }

    /**
     * Convert JPA entity to domain entity.
     */
    public ResearchAgentDecision toDomain() {
        // Use full constructor for persistence layer reconstruction
        return new ResearchAgentDecision(
            id,
            researchExecutionId,
            ResearchAgentType.valueOf(agentType),
            advice,
            reasoning,
            decidedAt,
            modelUsed,
            promptTokens,
            completionTokens,
            totalTokens,
            rawCostUsd,
            toolCalls
        );
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getResearchExecutionId() {
        return researchExecutionId;
    }

    public void setResearchExecutionId(UUID researchExecutionId) {
        this.researchExecutionId = researchExecutionId;
    }

    public ResearchExecutionJpaEntity getResearchExecution() {
        return researchExecution;
    }

    public void setResearchExecution(ResearchExecutionJpaEntity researchExecution) {
        this.researchExecution = researchExecution;
    }

    public String getAgentType() {
        return agentType;
    }

    public void setAgentType(String agentType) {
        this.agentType = agentType;
    }

    public String getAdvice() {
        return advice;
    }

    public void setAdvice(String advice) {
        this.advice = advice;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(Instant decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public BigDecimal getRawCostUsd() {
        return rawCostUsd;
    }

    public void setRawCostUsd(BigDecimal rawCostUsd) {
        this.rawCostUsd = rawCostUsd;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }
}
