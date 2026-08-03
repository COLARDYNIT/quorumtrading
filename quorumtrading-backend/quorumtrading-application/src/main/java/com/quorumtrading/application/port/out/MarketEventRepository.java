package com.quorumtrading.application.port.out;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.quorumtrading.model.valueobject.Symbol;
import com.quorumtrading.model.valueobject.TimeRange;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for MarketEvent entities.
 * Implemented by persistence adapters to provide data access.
 */
public interface MarketEventRepository {
    MarketEvent save(MarketEvent event);

    /**
     * Save a PRICE event coming from the periodic latest-quote collector, REPLACING any prior
     * intraday quote rows for the same symbol and calendar day instead of accumulating one row
     * per poll (the collector fires every few minutes — plain save() would leave hundreds of
     * permanent intraday rows per ticker per day next to the canonical midnight daily bar).
     * The midnight daily bar itself is never touched. Non-PRICE events delegate to save().
     *
     * <p>Default delegates to {@link #save(MarketEvent)} so test fakes keep working; the
     * persistence adapter overrides it with the replace-in-place behavior.</p>
     */
    default MarketEvent saveLatestPriceQuote(MarketEvent event) {
        return save(event);
    }
    List<MarketEvent> findByTickersAndTimeRange(Set<String> cryptoTickers, Set<String> stockTickers, Instant from, Instant to, EventType eventType, Integer limit);

    /**
     * Find content hashes for deduplication within a time range.
     * Used by market data adapters to filter out duplicate events.
     */
    Set<String> findContentHashesByTimeRange(Instant start, Instant end);

    /**
     * Find market events by source pattern, symbol, and time range.
     * Supports LIKE patterns for source matching (e.g., "reddit-%").
     */
    List<MarketEvent> findBySourcePatternAndSymbolAndTimeRange(String sourcePattern, String symbol, Instant start, Instant end);

    /**
     * Find market events by time range and symbols.
     * Used for data availability analysis.
     *
     * @param timeRange the time range to search within
     * @param symbols the set of symbols to filter by
     * @return list of market events matching the criteria
     */
    List<MarketEvent> findByTimeRangeAndSymbols(TimeRange timeRange, Set<Symbol> symbols);

    /**
     * Count market events for a specific symbol, event type, and time range.
     * Used for validating data availability before starting simulations.
     *
     * @param symbol the symbol to count events for
     * @param eventType the type of events to count
     * @param startDate the start of the time range
     * @param endDate the end of the time range
     * @return the count of events matching the criteria
     */
    long countEventsInRange(Symbol symbol, EventType eventType, Instant startDate, Instant endDate);

    /**
     * Find the earliest price event timestamp for a given symbol.
     * Used to clamp the indicator warmup range to avoid false-positive gap detection
     * for dates before data actually exists.
     *
     * @param symbol the symbol to query
     * @return the earliest price event timestamp, or empty if no data exists
     */
    Optional<Instant> findEarliestPriceDate(Symbol symbol);
}
