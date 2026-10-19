package com.quorumtrading.model.enums;

/**
 * Classifies a market-data gap by whether (and why) it can be filled.
 *
 * <ul>
 *   <li>{@link #ACTIONABLE} — a provider supports this gap and the range is in the
 *       past/present, so a backfill can fill it now.</li>
 *   <li>{@link #FUTURE} — the gap's range starts after today; the future cannot be
 *       backfilled.</li>
 *   <li>{@link #NO_SOURCE} — no provider supports this (symbol, dataType)
 *       combination at all.</li>
 *   <li>{@link #NO_CREDENTIALED_PROVIDER} — providers exist that could serve this gap,
 *       but none of them is switched on and holding credentials. Fixable by
 *       configuration (set a market-data API key), not by picking another range.
 *       Deliberately distinct from {@link #BEFORE_HORIZON}: a missing key is a
 *       deployment problem, and folding it into a "permanently unavailable" data
 *       floor tells a keyless install that 2021 stock data does not exist when a
 *       free API key would fetch it.</li>
 *   <li>{@link #BEFORE_HORIZON} — the gap lies entirely before the symbol's measured
 *       provider coverage; no provider has data that far back.</li>
 * </ul>
 */
public enum GapClassification {
    ACTIONABLE,
    FUTURE,
    NO_SOURCE,
    NO_CREDENTIALED_PROVIDER,
    BEFORE_HORIZON
}
