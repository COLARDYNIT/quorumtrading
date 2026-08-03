/**
 * Saxo Bank (EU) market-data adapter.
 *
 * <p>Implements {@code MarketDataPort} for Saxo Bank's OpenApi — {@code InfoPrices}
 * (live quotes) and {@code Charts} (historical backfill) — so EU instruments are
 * priced from the same venue, currency and clock they trade on. The adapter is
 * discovered by {@code CdiMarketDataPortDiscovery.findByProviderName("saxo")} and
 * registered as {@code @ApplicationScoped @Named("saxo")}.
 *
 * <p>This module is an empty scaffold (work package
 * {@code P0-saxo-md-module-shell}); the REST client, DTOs and adapter logic land
 * in later Saxo market-data work packages ({@code MD1}+). Adapters must not depend
 * on other adapters (hexagonal layering, enforced by ArchUnit).
 */
package com.quorumtrading.adapter.marketdata.saxo;
