package com.quorumtrading.application.usecase.dto;

import com.quorumtrading.model.entity.ConsensusDecision;
import com.quorumtrading.model.entity.LLMDecision;
import com.quorumtrading.model.entity.LlmParseFailure;

import java.util.ArrayList;
import java.util.List;

/**
 * Response containing a consensus decision with all its associated agent LLM decisions.
 * This aggregates the orchestrator's final decision with the individual agent recommendations
 * that contributed to the consensus.
 */
public class ConsensusWithAgentsResponse {

    private final ConsensusDecision consensusDecision;
    private final List<LLMDecision> agentDecisions;
    private final List<LlmParseFailure> parseFailures;

    public ConsensusWithAgentsResponse(ConsensusDecision consensusDecision, List<LLMDecision> agentDecisions) {
        this(consensusDecision, agentDecisions, List.of());
    }

    public ConsensusWithAgentsResponse(ConsensusDecision consensusDecision, List<LLMDecision> agentDecisions,
                                        List<LlmParseFailure> parseFailures) {
        this.consensusDecision = consensusDecision;
        this.agentDecisions = new ArrayList<>(agentDecisions);
        this.parseFailures = parseFailures != null ? List.copyOf(parseFailures) : List.of();
    }

    /**
     * Get the consensus decision (final orchestrated decision).
     */
    public ConsensusDecision getConsensusDecision() {
        return consensusDecision;
    }

    /**
     * Get the list of agent LLM decisions that contributed to this consensus.
     * Typically includes: Technical, Fundamental, Sentiment, and Orchestrator agents.
     */
    public List<LLMDecision> getAgentDecisions() {
        return new ArrayList<>(agentDecisions);
    }

    /**
     * Get the list of LLM parse failures that occurred during this consensus round.
     * Empty unless explicitly requested via includeParseFailures=true.
     */
    public List<LlmParseFailure> getParseFailures() {
        return parseFailures;
    }
}
