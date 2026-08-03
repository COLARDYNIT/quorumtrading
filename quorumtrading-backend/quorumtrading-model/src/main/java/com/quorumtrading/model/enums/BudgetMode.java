package com.quorumtrading.model.enums;

/**
 * How a recurring investment plan decides how much cash to allocate on each tick.
 *
 * <ul>
 *   <li>{@code FIXED_AMOUNT} — invest exactly {@code plan.amount} or skip if buying power is insufficient.</li>
 *   <li>{@code FIXED_OR_PARTIAL} — invest up to {@code plan.amount}, accepting less when buying power is short.</li>
 *   <li>{@code ALL_AVAILABLE} — invest the entire {@code BrokerAccount.availableBalance} (subject to a dust floor).</li>
 * </ul>
 */
public enum BudgetMode {
    FIXED_AMOUNT,
    FIXED_OR_PARTIAL,
    ALL_AVAILABLE
}
