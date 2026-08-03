package com.quorumtrading.adapter.rest.dto.response;

import java.util.List;

/**
 * REST API response DTO that aggregates a consensus decision with its associated agent decisions.
 * This DTO combines ConsensusDecisionResponse with a list of LLMDecisionResponse objects
 * to provide a complete view of the orchestrator's decision-making process.
 */
public class ConsensusWithAgentsResponseDTO {

    /**
     * The consensus decision made by the orchestrator
     */
    public ConsensusDecisionResponse consensusDecision;

    /**
     * All agent LLM decisions that contributed to this consensus
     */
    public List<LLMDecisionResponse> agentDecisions;

    /**
     * Default constructor for Jackson serialization
     */
    public ConsensusWithAgentsResponseDTO() {
    }

    /**
     * Constructor with all fields
     */
    public ConsensusWithAgentsResponseDTO(
            ConsensusDecisionResponse consensusDecision,
            List<LLMDecisionResponse> agentDecisions
    ) {
        this.consensusDecision = consensusDecision;
        this.agentDecisions = agentDecisions;
    }
}
