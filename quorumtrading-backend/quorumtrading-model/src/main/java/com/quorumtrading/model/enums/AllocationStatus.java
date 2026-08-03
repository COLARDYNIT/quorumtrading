package com.quorumtrading.model.enums;

/**
 * Outcome of one {@code InvestmentAllocation} attempt.
 *
 * <p>Every plan execution writes one allocation row regardless of success — the
 * trail must always reflect what happened (or didn't), with enough context for
 * the user to act.</p>
 */
public enum AllocationStatus {
    /** Funded successfully (TOP_UP_EXISTING or NEW_SIMULATION). */
    EXECUTED,
    /**
     * Funded, but the executing broker forced whole-share rounding (Saxo equities/ETFs, D3)
     * so only part of the intended amount was deployed this tick; the sub-share residue is
     * carried to the next tick. The deployed portion is recorded in
     * {@code InvestmentAllocation.deployedAmount}, with {@code fractionalConstraint=true}.
     */
    EXECUTED_PARTIAL_DEPLOY,
    /** Skipped because the broker had less buying power than the plan required. */
    SKIPPED_INSUFFICIENT_FUNDS,
    /**
     * Skipped because this plan already funded today, so no money moved.
     *
     * <p>Two guards produce this status. The primary one probes {@code investment_allocations} for
     * an already-funded row on the same UTC day, which covers both funding paths; the
     * contribution-level {@code (planId, contributionDate)} key remains as defence in depth for
     * top-ups. The allocation probe exists because the {@code NEW_SIMULATION} path writes no
     * contribution row at all — the cash is the new simulation's initial capital — so the
     * contribution key could not see the first funding, and exactly one extra funding per day
     * slipped through.</p>
     *
     * <p>This is a successful no-op, not a failure: it is what makes a retried scheduler tick
     * safe. It needs its own value because the alternative was recording it as {@code EXECUTED}
     * with the full plan amount, which is what three same-day ticks did — writing three
     * {@code EXECUTED / $500} rows against one real $500 contribution and reporting $1500 in the
     * timeline. The row carries {@code actualAmount} zero and names the contribution it
     * deduplicated against.</p>
     */
    SKIPPED_ALREADY_EXECUTED_TODAY,
    /** Skipped because the picker port returned no/invalid strategy. */
    SKIPPED_PICKER_FAILED,
    /** Skipped because no strategy met the plan's eligibility constraints. */
    SKIPPED_NO_ELIGIBLE_STRATEGY,
    /**
     * Defensive: picker returned a strategy whose tickers violate the plan's allow/block list.
     * Pickers pre-filter by policy, so this status only fires if the picker misbehaves.
     */
    SKIPPED_TICKER_POLICY_VIOLATION,
    /** Broker rejected the order (e.g. exchange down, validation failure). */
    FAILED_BROKER_ERROR
}
