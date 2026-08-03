package com.quorumtrading.model.enums;

/**
 * Classifies a market-data gap by whether (and why) it can be filled.
 *
 * <ul>
 *   <li>{@link #ACTIONABLE} — a provider supports this gap and the range is in the
 *       past/present, so a backfill can fill it now.</li>
 *   <li>{@link #FUTURE} — the gap's range starts after today; the future cannot be
 *       backfilled.</li>
 *   <li>{@link #NO_SOURCE} — no configured provider supports this (symbol, dataType)
 *       combination.</li>
 *   <li>{@link #BEFORE_HORIZON} — the gap lies entirely before the symbol's measured
 *       provider coverage; no provider has data that far back.</li>
 * </ul>
 */
public enum GapClassification {
    ACTIONABLE,
    FUTURE,
    NO_SOURCE,
    BEFORE_HORIZON
}
