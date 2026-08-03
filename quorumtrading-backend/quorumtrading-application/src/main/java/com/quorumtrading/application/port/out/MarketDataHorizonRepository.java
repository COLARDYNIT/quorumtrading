package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.GranularityBucket;
import com.quorumtrading.model.marketdata.DataHorizon;
import com.quorumtrading.model.valueobject.Symbol;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Outbound port for persisting and reading measured provider data horizons.
 *
 * <p>Keyed by (provider, symbol, eventType, granularityBucket). {@link #upsert}
 * replaces the existing row for that key.</p>
 */
public interface MarketDataHorizonRepository {

    Optional<DataHorizon> find(String provider, Symbol symbol, EventType type, GranularityBucket bucket);

    void upsert(DataHorizon horizon);

    /**
     * Bulk lookup of horizons for many symbols at one (type, bucket). Used by the
     * REST/UI layer to annotate multiple symbols without N round-trips.
     */
    List<DataHorizon> findForSymbols(Set<Symbol> symbols, EventType type, GranularityBucket bucket);
}
