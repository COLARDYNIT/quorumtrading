package com.quorumtrading.model.enums;

/**
 * Domain-level classification of user notifications.
 *
 * <p>Used both as a routing key for {@code NotificationPreference} (per-type
 * channel selection) and as the discriminator for typed payload rendering.</p>
 *
 * <p>Phase 1 ships with the investment-plan vocabulary plus a {@code GENERIC}
 * catch-all; later phases extend this enum as new feature areas grow.</p>
 */
public enum NotificationType {
    /** A scheduled investment was funded successfully (top-up or new sim). */
    INVESTMENT_EXECUTED,
    /** A scheduled investment was skipped (insufficient funds, no eligible strategy, etc.). */
    INVESTMENT_SKIPPED,
    /** A scheduled investment failed during broker submission. */
    INVESTMENT_FAILED,
    /** An investment plan was paused (system- or user-initiated). */
    PLAN_PAUSED,
    /**
     * A consensus decision in a LIVE simulation produced an executed BUY or SELL.
     * Gated per-simulation by {@code simulations.notify_decisions}; HOLDs never fire.
     */
    SIMULATION_DECISION,
    /**
     * A simulation was paused because the LLM spend budget was exhausted
     * (BudgetDepleted or DailySpendLimit). Always fires regardless of opt-in —
     * the user controls only the channel routing.
     */
    SIMULATION_PAUSED_BUDGET,
    /**
     * The LLM spend budget for a simulation dropped below a configured warning
     * threshold. One-shot per crossing — hysteresis flag prevents re-fires
     * until budget recovers above a higher reset threshold.
     */
    SIMULATION_LOW_BUDGET,
    /**
     * A market-research run produced and persisted a new strategy.
     * Fires once per strategy created; the user implicitly opted in by starting
     * the research execution.
     */
    MARKET_RESEARCH_STRATEGY_CREATED,
    /**
     * A broker connection's OAuth refresh chain broke and the account now needs the
     * user to re-authorize before it can trade again (the account is moved to
     * {@code NEEDS_REAUTH}). Always fires regardless of opt-in because unattended
     * trading silently stops until the user acts — the user controls only the channel
     * routing. Defaults to IN_APP + EMAIL.
     */
    BROKER_REAUTH_REQUIRED,
    /** Catch-all for system messages that don't fit a more specific type. */
    GENERIC
}
