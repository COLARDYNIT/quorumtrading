package com.quorumtrading.model.enums;

/**
 * Broker-neutral instrument asset type. Mirrors the distinctions Saxo's
 * {@code AssetType} draws (and that fractional-share / pricing rules key off),
 * while staying provider-neutral.
 *
 * <ul>
 *   <li>{@link #STOCK} — cash equity / single share.</li>
 *   <li>{@link #ETF} — exchange-traded fund.</li>
 *   <li>{@link #CRYPTO_FX} — crypto FX pair (the one asset type that supports
 *       fractional units at Saxo).</li>
 *   <li>{@link #CFD_INDEX} — contract-for-difference on an index.</li>
 *   <li>{@link #BOND} — fixed-income instrument.</li>
 *   <li>{@link #FUND} — mutual fund (fractional units).</li>
 * </ul>
 *
 * <p>Pure domain enum — NO JPA/JSON/Jackson annotations.</p>
 */
public enum AssetType {
    STOCK,
    ETF,
    CRYPTO_FX,
    CFD_INDEX,
    BOND,
    FUND
}
