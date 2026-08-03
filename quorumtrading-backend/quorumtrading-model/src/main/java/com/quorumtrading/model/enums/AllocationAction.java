package com.quorumtrading.model.enums;

/**
 * How a successfully-executed allocation was applied.
 */
public enum AllocationAction {
    /** Cash was added to an existing LIVE simulation on the chosen strategy. */
    TOP_UP_EXISTING,
    /** A brand-new LIVE simulation was created with the allocated cash as initial capital. */
    NEW_SIMULATION
}
