package com.quorumtrading.model.enums;

/**
 * Enum representing the consensus method used for decision making.
 * Each method determines HOW the final trading decision is computed from agent votes.
 * The AI agents always run and provide recommendations - this setting controls the decision logic.
 */
public enum ConsensusMethod {
    /**
     * Simple majority voting: the action with the most agent votes wins.
     * No orchestrator LLM call is made — agents' votes directly determine the outcome.
     * On tie → HOLD. HOLD agents with confidence below consensus_threshold abstain.
     * Alias: MAJORITY_VOTE also works in YAML.
     */
    MAJORITY(
        "Majority Vote",
        "Agent votes determine the final action directly. No orchestrator LLM call. " +
        "The action with the most votes wins. Ties default to HOLD. " +
        "HOLD agents with confidence below consensus_threshold abstain (don't vote)."
    ),

    /**
     * Orchestrator override: an LLM orchestrator reviews all agent recommendations
     * and makes the final trading decision. The orchestrator can follow or override
     * the agents' majority opinion.
     */
    ORCHESTRATOR_OVERRIDE(
        "Orchestrator Override",
        "An LLM orchestrator reviews all agent recommendations and makes the final decision. " +
        "The orchestrator can follow or override the agents' majority opinion."
    );

    private final String displayName;
    private final String description;

    ConsensusMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Whether reaching consensus by this method costs an extra orchestrator LLM call per round.
     *
     * <p>MAJORITY tallies the agents' votes in code and calls no orchestrator, so anything
     * counting calls — cost estimates, budget projections — must not add one. Counting it
     * overstated every MAJORITY backtest estimate by one call in (agents + 1): 25% with the
     * default three agents.</p>
     *
     * @return true when an orchestrator LLM call is made per consensus round
     */
    public boolean usesOrchestratorLlm() {
        return this == ORCHESTRATOR_OVERRIDE;
    }
}
