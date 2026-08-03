package com.quorumtrading.model.enums;

/**
 * Enum representing the types of decisions that can be made during market research orchestration.
 *
 * This enum is used when the research coordinator (LLM) makes a final decision about
 * whether to create a trading strategy based on the research findings.
 */
public enum ResearchDecisionType {
    /**
     * Decision to create a new trading strategy based on the research findings.
     * This indicates that the opportunity is viable and a strategy should be implemented.
     */
    CREATE_STRATEGY,

    /**
     * Decision that no viable opportunity exists based on the research findings.
     * This indicates that the research did not reveal sufficient evidence to create a strategy.
     */
    NO_OPPORTUNITY
}
