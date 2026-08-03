package com.quorumtrading.model.enums;

/**
 * Lifecycle of a recurring {@code InvestmentPlan}.
 *
 * <ul>
 *   <li>{@code ACTIVE} — eligible for the next scheduled execution.</li>
 *   <li>{@code PAUSED} — temporarily disabled. Scheduler skips, user can resume.</li>
 *   <li>{@code TERMINATED} — terminal state; the plan no longer executes.
 *       Existing allocations remain for audit.</li>
 * </ul>
 */
public enum InvestmentPlanStatus {
    ACTIVE,
    PAUSED,
    TERMINATED
}
