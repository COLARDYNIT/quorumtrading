package com.quorumtrading.model.enums;

/**
 * Represents the type of order to execute.
 */
public enum OrderType {
    /**
     * Market order - execute immediately at current market price.
     */
    MARKET,

    /**
     * Limit order - execute only at specified price or better.
     */
    LIMIT
}
