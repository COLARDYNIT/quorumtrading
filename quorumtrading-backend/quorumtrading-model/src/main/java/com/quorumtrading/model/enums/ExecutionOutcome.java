package com.quorumtrading.model.enums;

/**
 * Represents the execution outcome of a consensus decision.
 * Used to track whether a trading decision was successfully executed or why it failed.
 */
public enum ExecutionOutcome {
    /**
     * Decision has not been executed yet (pending).
     */
    PENDING,

    /**
     * Trade was successfully executed by the broker.
     */
    EXECUTED,

    /**
     * Decision was HOLD - no trade required. Also covers a SELL with nothing held
     * (there is genuinely nothing to do). A consensus BUY that the engine refused is
     * NOT this — see {@link #BLOCKED_BY_RISK_LIMITS} and {@link #BELOW_MINIMUM_TRADE_VALUE}.
     */
    NO_TRADE_REQUIRED,

    /**
     * Consensus wanted to BUY, but the strategy's own risk controls (position_limits or
     * reserve_cash_percentage) left no room, so the engine refused the trade. The decision
     * notes name the binding control and its numbers.
     */
    BLOCKED_BY_RISK_LIMITS,

    /**
     * Consensus wanted to trade, but the resulting order was too small to execute: below
     * the per-asset-class minimum trade value, or under one whole share on a broker that
     * fills whole shares only.
     */
    BELOW_MINIMUM_TRADE_VALUE,

    /**
     * Trade was rejected by the broker (e.g., insufficient funds, no market data, invalid quantity).
     */
    REJECTED,

    /**
     * Consensus was skipped due to missing market data or other temporary condition.
     * Simulation continues to next evaluation period.
     */
    SKIPPED
}
