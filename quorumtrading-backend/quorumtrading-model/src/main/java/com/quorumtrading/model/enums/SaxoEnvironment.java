package com.quorumtrading.model.enums;

/**
 * Saxo OpenAPI environment a broker account / credential set targets.
 *
 * <ul>
 *   <li>{@link #SIM} — the Saxo developer simulation environment (paper trading).</li>
 *   <li>{@link #LIVE} — the Saxo live environment (real money).</li>
 * </ul>
 *
 * <p>Maps one-to-one onto the Saxo {@link BrokerType} constants
 * ({@code SAXO_SIM} → {@link #SIM}, {@code SAXO_LIVE} → {@link #LIVE}) and selects the
 * OAuth authorization / OpenApi base URLs at the adapter layer.
 */
public enum SaxoEnvironment {
    SIM,
    LIVE
}
