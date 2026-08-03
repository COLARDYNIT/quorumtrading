package com.quorumtrading.model.enums;

/**
 * Trading mode for strategy execution scheduling.
 * Determines whether a strategy uses interval-based (24/7) or scheduled (market hours) execution.
 *
 * <p><b>Automatic Inference:</b></p>
 * Trading mode is automatically inferred from the strategy configuration:
 * <ul>
 *   <li>If <code>stock_tickers</code> present → STOCK mode (requires <code>live_schedule</code>)</li>
 *   <li>If only <code>crypto_tickers</code> → CRYPTO mode (uses <code>live_schedule.interval</code>)</li>
 * </ul>
 *
 * <p><b>Scheduling Behavior:</b></p>
 * <ul>
 *   <li><b>CRYPTO:</b> Interval-based execution, 24/7 trading</li>
 *   <li><b>STOCK:</b> Scheduled execution at specific times within market hours</li>
 * </ul>
 *
 * <p><b>Design Decision:</b></p>
 * Mixed crypto+stock strategies are NOT supported. A strategy must trade one asset type.
 * This simplifies scheduling logic and market data requirements.
 */
public enum TradingMode {

    /**
     * 24/7 cryptocurrency trading with interval-based execution.
     * Consensus evaluations occur at fixed intervals (e.g., every 3600 seconds).
     * No market hours restrictions apply.
     */
    CRYPTO,

    /**
     * Stock trading with scheduled execution during market hours.
     * Consensus evaluations occur at specific times on specific days (e.g., Monday-Friday at 10:00 AM ET).
     * Market hours validation prevents trading outside allowed times.
     */
    STOCK;

    /**
     * Returns true if this mode requires market hours validation.
     *
     * @return true for STOCK, false for CRYPTO
     */
    public boolean requiresMarketHours() {
        return this == STOCK;
    }

    /**
     * Returns true if this mode uses interval-based scheduling.
     *
     * @return true for CRYPTO, false for STOCK
     */
    public boolean usesIntervalScheduling() {
        return this == CRYPTO;
    }
}
