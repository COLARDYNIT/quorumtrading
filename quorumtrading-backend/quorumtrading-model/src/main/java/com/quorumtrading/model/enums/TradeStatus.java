package com.quorumtrading.model.enums;

/**
 * Represents the execution status of a trade.
 */
public enum TradeStatus {
    /**
     * Trade was fully executed.
     */
    EXECUTED,

    /**
     * Trade was rejected (insufficient funds, invalid parameters, etc.).
     */
    REJECTED,

    /**
     * Trade was partially filled (partial execution).
     */
    PARTIAL_FILL,

    /**
     * Trade is pending execution.
     */
    PENDING
}
