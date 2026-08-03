package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.valueobject.ResearchAgentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity representing an agent's decision during market research.
 * Child entity of ResearchExecution.
 */
public class ResearchAgentDecision {

    private final UUID id;
    private final UUID researchExecutionId;
    private final ResearchAgentType agentType;
    private final String advice;
    private final String reasoning;
    private final Instant decidedAt;

    // LLM usage/cost tracking
    private final String modelUsed;
    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;
    private final BigDecimal rawCostUsd;

    // Tool calls JSON (e.g., [{"tool":"get_indicators","args":{...},"result":"..."}])
    private final String toolCalls;

    /**
     * Constructor without LLM usage (for backward compatibility).
     */
    public ResearchAgentDecision(
        UUID researchExecutionId,
        ResearchAgentType agentType,
        String advice,
        String reasoning
    ) {
        this(researchExecutionId, agentType, advice, reasoning, null, null);
    }

    /**
     * Constructor with LLM usage tracking.
     */
    public ResearchAgentDecision(
        UUID researchExecutionId,
        ResearchAgentType agentType,
        String advice,
        String reasoning,
        LLMUsage llmUsage
    ) {
        this(researchExecutionId, agentType, advice, reasoning, llmUsage, null);
    }

    /**
     * Constructor with LLM usage tracking and tool calls.
     */
    public ResearchAgentDecision(
        UUID researchExecutionId,
        ResearchAgentType agentType,
        String advice,
        String reasoning,
        LLMUsage llmUsage,
        String toolCalls
    ) {
        this.id = UUID.randomUUID();

        if (researchExecutionId == null) {
            throw new IllegalArgumentException("Research execution ID cannot be null");
        }
        if (agentType == null) {
            throw new IllegalArgumentException("Agent type cannot be null");
        }
        if (advice == null || advice.isBlank()) {
            throw new IllegalArgumentException("Advice cannot be blank");
        }
        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException("Reasoning cannot be blank");
        }

        this.researchExecutionId = researchExecutionId;
        this.agentType = agentType;
        this.advice = advice;
        this.reasoning = reasoning;
        this.decidedAt = Instant.now();
        this.toolCalls = toolCalls;

        // Set LLM usage fields
        if (llmUsage != null) {
            this.modelUsed = llmUsage.model();
            this.promptTokens = llmUsage.promptTokens();
            this.completionTokens = llmUsage.completionTokens();
            this.totalTokens = llmUsage.totalTokens();
            this.rawCostUsd = llmUsage.rawCost();
        } else {
            this.modelUsed = null;
            this.promptTokens = 0;
            this.completionTokens = 0;
            this.totalTokens = 0;
            this.rawCostUsd = BigDecimal.ZERO;
        }
    }

    /**
     * Full constructor for persistence layer reconstruction.
     */
    public ResearchAgentDecision(
        UUID id,
        UUID researchExecutionId,
        ResearchAgentType agentType,
        String advice,
        String reasoning,
        Instant decidedAt,
        String modelUsed,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal rawCostUsd,
        String toolCalls
    ) {
        this.id = id;
        this.researchExecutionId = researchExecutionId;
        this.agentType = agentType;
        this.advice = advice;
        this.reasoning = reasoning;
        this.decidedAt = decidedAt;
        this.modelUsed = modelUsed;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.rawCostUsd = rawCostUsd != null ? rawCostUsd : BigDecimal.ZERO;
        this.toolCalls = toolCalls;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getResearchExecutionId() { return researchExecutionId; }
    public ResearchAgentType getAgentType() { return agentType; }
    public String getAdvice() { return advice; }
    public String getReasoning() { return reasoning; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getModelUsed() { return modelUsed; }
    public int getPromptTokens() { return promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public BigDecimal getRawCostUsd() { return rawCostUsd; }
    public String getToolCalls() { return toolCalls; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResearchAgentDecision that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
