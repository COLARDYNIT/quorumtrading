package com.quorumtrading.model.service;

import java.math.BigDecimal;

/**
 * Process-wide markup rate applied to raw LLM cost to produce the billed
 * (user-facing) price.
 *
 * <p>The rate is deployment configuration, not code: it is initialized once at
 * startup from {@code quorumtrading.billing.markup-rate} and defaults to 1.0
 * (billed == raw), so a self-hosted install is never charged more than the
 * provider's own price. A hosted deployment sets its commercial rate through
 * the environment.</p>
 */
public final class PlatformMarkup {

    /** Rate of 1.0 — billed cost equals raw cost. */
    public static final BigDecimal NO_MARKUP = BigDecimal.ONE;

    private static volatile BigDecimal rate = NO_MARKUP;

    private PlatformMarkup() {
    }

    /** The markup rate currently in effect (multiplier on raw cost, e.g. 1.25). */
    public static BigDecimal rate() {
        return rate;
    }

    /**
     * Set the markup rate for this process. Called once at startup from
     * deployment configuration; a rate below 1.0 would bill users less than
     * the provider charges and is rejected.
     */
    public static void configure(BigDecimal newRate) {
        if (newRate == null || newRate.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Markup rate must be at least 1.0, got: " + newRate);
        }
        rate = newRate;
    }

    /** Restore the default rate. Intended for test isolation. */
    public static void reset() {
        rate = NO_MARKUP;
    }
}
