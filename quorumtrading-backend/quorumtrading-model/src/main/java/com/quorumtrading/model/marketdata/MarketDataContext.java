package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.enums.InvestmentType;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Domain aggregate representing all market data available for consensus decision-making.
 * <p>
 * Provides a rich, type-safe API for querying market events by symbol, timeframe, and event type.
 * Replaces generic Map<String, Object> structures with domain-driven design.
 * <p>
 * Immutable and validated on construction - cannot be created with invalid data.
 * Uses ubiquitous language from the trading domain.
 */
public class MarketDataContext {

    private final Map<Symbol, Map<Timeframe, List<MarketEvent>>> eventsBySymbolAndTimeframe;
    private final Instant analysisTimestamp;
    private final Set<Symbol> availableSymbols;

    // Lazy caches for expensive computations — populated on first access per (symbol, timeframe)
    // Must be thread-safe: agents access these concurrently via CompletableFuture in ReachConsensusService
    private final Map<Symbol, Map<Timeframe, PriceStatistics>> statsCache = new ConcurrentHashMap<>();
    private final Map<Symbol, Optional<BigDecimal>> latestPriceCache = new ConcurrentHashMap<>();

    /**
     * Private constructor - use factory methods to create instances.
     */
    private MarketDataContext(
            Map<Symbol, Map<Timeframe, List<MarketEvent>>> events,
            Instant analysisTimestamp) {

        if (events == null) {
            throw new IllegalArgumentException("events cannot be null");
        }
        if (analysisTimestamp == null) {
            throw new IllegalArgumentException("analysisTimestamp cannot be null");
        }

        // Make defensive copy with immutable collections
        this.eventsBySymbolAndTimeframe = events.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                e -> e.getValue().entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        tf -> List.copyOf(tf.getValue())
                    ))
            ));

        this.analysisTimestamp = analysisTimestamp;
        this.availableSymbols = Set.copyOf(events.keySet());
    }

    /**
     * Get all market events for a specific symbol in a timeframe.
     *
     * @param symbol Symbol to query
     * @param timeframe Timeframe to query
     * @return List of events (empty if none found)
     */
    public List<MarketEvent> getEventsForSymbol(Symbol symbol, Timeframe timeframe) {
        return eventsBySymbolAndTimeframe
            .getOrDefault(symbol, Map.of())
            .getOrDefault(timeframe, List.of());
    }

    /**
     * Get all market events for a symbol across all timeframes.
     *
     * @param symbol Symbol to query
     * @return List of all events for this symbol
     */
    public List<MarketEvent> getAllEventsForSymbol(Symbol symbol) {
        return eventsBySymbolAndTimeframe
            .getOrDefault(symbol, Map.of())
            .values()
            .stream()
            .flatMap(List::stream)
            .distinct()
            .sorted(Comparator.comparing(MarketEvent::timestamp))
            .toList();
    }

    /**
     * Get price events for a symbol in a specific timeframe.
     *
     * @param symbol Symbol to query
     * @param timeframe Timeframe to query
     * @return List of price update events
     */
    public List<MarketEvent> getPriceEvents(Symbol symbol, Timeframe timeframe) {
        return getEventsForSymbol(symbol, timeframe).stream()
            .filter(event -> event.eventType() == EventType.PRICE)
            .toList();
    }

    /**
     * Get news events (NEWS and SOCIAL) for a symbol with balanced representation.
     * Returns mix of traditional news and social media to avoid recency bias.
     * Uses stratified sampling: 70% traditional news (RSS/NewsAPI), 30% social (Reddit).
     *
     * @param symbol Symbol to query
     * @param limit Maximum number of news items to return
     * @return List of news events with balanced NEWS/SOCIAL mix, newest first
     */
    public List<MarketEvent> getNewsEvents(Symbol symbol, int limit) {
        List<MarketEvent> newsEvents = new ArrayList<>();

        // Combine 24h and 7d timeframes for news
        newsEvents.addAll(getEventsForSymbol(symbol, Timeframe.LAST_24H));
        newsEvents.addAll(getEventsForSymbol(symbol, Timeframe.LAST_7D));

        // Separate NEWS from SOCIAL to prevent recency bias
        List<MarketEvent> traditionalNews = newsEvents.stream()
            .filter(event -> event.eventType() == EventType.NEWS)
            .sorted(Comparator.comparing(MarketEvent::timestamp).reversed())
            .distinct()
            .toList();

        List<MarketEvent> socialPosts = newsEvents.stream()
            .filter(event -> event.eventType() == EventType.SOCIAL)
            .sorted(Comparator.comparing(MarketEvent::timestamp).reversed())
            .distinct()
            .toList();

        // Balanced sampling: 70% traditional news, 30% social media
        // This prevents Reddit from dominating due to collection timing differences
        // Edge case handling: if one category is empty, allocate all to the other
        int newsLimit;
        int socialLimit;

        if (socialPosts.isEmpty()) {
            // No social posts - allocate full limit to news
            newsLimit = limit;
            socialLimit = 0;
        } else if (traditionalNews.isEmpty()) {
            // No traditional news - allocate full limit to social
            newsLimit = 0;
            socialLimit = limit;
        } else {
            // Both exist - use 70/30 split, with remainder allocation
            newsLimit = (int) Math.ceil(limit * 0.7);
            socialLimit = (int) Math.ceil(limit * 0.3);

            // If we have fewer items in one category, reallocate remainder to other
            int actualNews = Math.min(newsLimit, traditionalNews.size());
            int actualSocial = Math.min(socialLimit, socialPosts.size());
            int total = actualNews + actualSocial;

            if (total < limit) {
                // We have room - allocate remainder
                int remainder = limit - total;
                if (traditionalNews.size() > actualNews) {
                    newsLimit += remainder;
                } else if (socialPosts.size() > actualSocial) {
                    socialLimit += remainder;
                }
            }
        }

        List<MarketEvent> balanced = new ArrayList<>();
        balanced.addAll(traditionalNews.stream().limit(newsLimit).toList());
        balanced.addAll(socialPosts.stream().limit(socialLimit).toList());

        // Sort combined result by timestamp DESC and return
        return balanced.stream()
            .sorted(Comparator.comparing(MarketEvent::timestamp).reversed())
            .toList();
    }

    /**
     * Get formatted news headlines for a symbol.
     * Formats: [timestamp] [source] headline
     *
     * @param symbol Symbol to query
     * @param limit Maximum number of headlines
     * @return List of formatted news headlines
     */
    public List<String> getNewsHeadlines(Symbol symbol, int limit) {
        return getNewsEvents(symbol, limit).stream()
            .map(this::formatNewsHeadline)
            .toList();
    }

    /**
     * Calculate price statistics for a symbol in a timeframe.
     *
     * @param symbol Symbol to analyze
     * @param timeframe Timeframe to analyze
     * @return Price statistics (empty if no data)
     */
    public PriceStatistics calculatePriceStatistics(Symbol symbol, Timeframe timeframe) {
        return statsCache
            .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(timeframe, tf -> computePriceStatistics(symbol, tf));
    }

    private PriceStatistics computePriceStatistics(Symbol symbol, Timeframe timeframe) {
        List<MarketEvent> allPriceEvents = allPriceEventsAscending(symbol);
        if (allPriceEvents.isEmpty()) {
            return PriceStatistics.emptyStatistics();
        }
        MarketEvent newest = allPriceEvents.get(allPriceEvents.size() - 1);

        List<MarketEvent> window = new ArrayList<>(getPriceEvents(symbol, timeframe));
        window.sort(Comparator.comparing(MarketEvent::timestamp));

        // Every timeframe must end at the newest known bar. The buckets are filled by independent,
        // separately-limited queries, so a window can stop short of the latest bar even when a
        // narrower one contains it — which produced a "30 day" range whose maximum was lower than
        // the "7 day" range, and a per-timeframe "Current" price that disagreed with the one the
        // Orchestrator was given. Anchoring each window to the newest bar also makes the reported
        // change mean what it says: movement from the start of the period to now.
        window.removeIf(event -> event.timestamp().isAfter(newest.timestamp()));
        if (window.isEmpty() || !window.get(window.size() - 1).timestamp().equals(newest.timestamp())) {
            window.add(newest);
        }

        // A strict wall-clock window can still leave a single bar — LAST_24H on stocks, where daily
        // bars are stamped 00:00Z but consensus runs mid-session, so only today's bar falls inside.
        // earliest == latest would report a flat 0.00%, making any "buy on an up-day" rule
        // unsatisfiable and reading as a broken strategy rather than a missing data point.
        if (window.size() < 2) {
            window = lastTwoPriceEvents(symbol, window);
        }

        List<BigDecimal> prices = window.stream()
            .map(this::extractPrice)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        return PriceStatistics.fromPrices(prices);
    }

    /**
     * The two most recent price events for a symbol across every timeframe, ascending.
     * <p>
     * Deliberately drawn from the union of all timeframes rather than the next-wider one. The
     * per-timeframe buckets are populated by independent, separately-limited queries, so a wider
     * bucket is not guaranteed to be a superset of a narrower one — picking "the next wider
     * timeframe" could therefore return an older pair than {@link #getLatestPrice} reports and make
     * the two disagree about the current price. Sharing {@link #allPriceEventsAscending} keeps them
     * consistent by construction.
     *
     * @param symbol  Symbol to query
     * @param current The (0- or 1-element) list found in the requested timeframe
     * @return The two most recent price events ascending, or {@code current} if fewer than two exist
     */
    private List<MarketEvent> lastTwoPriceEvents(Symbol symbol, List<MarketEvent> current) {
        List<MarketEvent> all = allPriceEventsAscending(symbol);
        if (all.size() >= 2) {
            return all.subList(all.size() - 2, all.size());
        }
        return all.isEmpty() ? current : all;
    }

    /**
     * Every distinct price event for a symbol, from all timeframes, oldest first.
     * Single source of truth for "what is the latest price" so that per-timeframe statistics and
     * {@link #getLatestPrice} can never disagree.
     *
     * @param symbol Symbol to query
     * @return Distinct price events in ascending timestamp order (empty if none)
     */
    private List<MarketEvent> allPriceEventsAscending(Symbol symbol) {
        return eventsBySymbolAndTimeframe
            .getOrDefault(symbol, Map.of())
            .values()
            .stream()
            .flatMap(List::stream)
            .filter(event -> event.eventType() == EventType.PRICE)
            .distinct()
            .sorted(Comparator.comparing(MarketEvent::timestamp))
            .toList();
    }

    /**
     * Get the latest price for a symbol (from most recent timeframe).
     *
     * @param symbol Symbol to query
     * @return Latest price if available
     */
    public Optional<BigDecimal> getLatestPrice(Symbol symbol) {
        return latestPriceCache.computeIfAbsent(symbol, this::computeLatestPrice);
    }

    private Optional<BigDecimal> computeLatestPrice(Symbol symbol) {
        // Take the newest price event across every timeframe rather than the newest within the first
        // non-empty one. The buckets come from independently limited queries, so the narrowest
        // non-empty window does not necessarily hold the newest bar. Sharing the same ordering as
        // computePriceStatistics guarantees the Technical agent's "Current" price and the
        // Orchestrator's "current price" describe the same bar.
        List<MarketEvent> all = allPriceEventsAscending(symbol);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return extractPrice(all.get(all.size() - 1));
    }

    /**
     * Get all symbols that have data in this context.
     */
    public Set<Symbol> getAvailableSymbols() {
        return availableSymbols;
    }

    /**
     * Check if data exists for a symbol.
     */
    public boolean hasDataForSymbol(Symbol symbol) {
        return availableSymbols.contains(symbol);
    }

    /**
     * Get the analysis timestamp for this context.
     */
    public Instant getAnalysisTimestamp() {
        return analysisTimestamp;
    }

    /**
     * Get total event count for a symbol.
     */
    public int getEventCount(Symbol symbol) {
        return eventsBySymbolAndTimeframe
            .getOrDefault(symbol, Map.of())
            .values()
            .stream()
            .mapToInt(List::size)
            .sum();
    }

    /**
     * Get event count by type for a symbol in a timeframe.
     */
    public Map<EventType, Long> getEventTypeCounts(Symbol symbol, Timeframe timeframe) {
        return getEventsForSymbol(symbol, timeframe).stream()
            .collect(Collectors.groupingBy(
                MarketEvent::eventType,
                Collectors.counting()
            ));
    }

    /**
     * Check if market data context is empty (no data for any symbol).
     */
    public boolean isEmpty() {
        return availableSymbols.isEmpty();
    }

    /**
     * Factory method: Create MarketDataContext from raw multi-timeframe data.
     * Organizes events by symbol and timeframe.
     *
     * @param eventsByTimeframe Raw events grouped by timeframe
     * @param symbols Symbols to include in the context
     * @param analysisTimestamp Reference timestamp for this analysis
     * @return Validated MarketDataContext
     */
    public static MarketDataContext from(
            Map<Timeframe, List<MarketEvent>> eventsByTimeframe,
            Set<String> symbols,
            Instant analysisTimestamp) {

        if (eventsByTimeframe == null || symbols == null) {
            throw new IllegalArgumentException("eventsByTimeframe and symbols cannot be null");
        }

        // Convert string symbols to Symbol value objects.
        //
        // The filter accepts ticker symbols and the special "MARKET" symbol for general market
        // news. It deliberately allows more than plain 1-5 letter equity tickers: a
        // "^[A-Z]{1,5}$" filter silently erased every concatenated crypto pair (SOLUSD, BTCUSD)
        // from the context, so agents were handed no price data whatsoever for those symbols
        // while still being asked to trade them.
        Set<Symbol> symbolObjects = symbols.stream()
            .filter(MarketDataContext::isUsableSymbol)
            .map(s -> new Symbol(s, InvestmentType.fromTicker(s)))
            .collect(Collectors.toSet());

        // Organize events by symbol and timeframe
        Map<Symbol, Map<Timeframe, List<MarketEvent>>> organized = new HashMap<>();

        for (Symbol symbol : symbolObjects) {
            Map<Timeframe, List<MarketEvent>> timeframeMap = new HashMap<>();

            for (Map.Entry<Timeframe, List<MarketEvent>> entry : eventsByTimeframe.entrySet()) {
                List<MarketEvent> symbolEvents = entry.getValue().stream()
                    .filter(event -> symbol.ticker().equals(event.symbol()))
                    .sorted(Comparator.comparing(MarketEvent::timestamp))
                    .toList();

                if (!symbolEvents.isEmpty()) {
                    timeframeMap.put(entry.getKey(), symbolEvents);
                }
            }

            if (!timeframeMap.isEmpty()) {
                organized.put(symbol, timeframeMap);
            }
        }

        return new MarketDataContext(organized, analysisTimestamp);
    }

    /**
     * Whether a raw ticker string can be turned into a usable {@link Symbol}.
     *
     * <p>Accepts equity tickers, crypto pairs in every form the platform stores them
     * (BTCUSD, BTC-USD, BTC/USD), venue-qualified EU symbols (ROOT:MIC) and the synthetic
     * "MARKET" symbol. Rejects only what cannot be a symbol at all — blank strings and
     * anything carrying whitespace.</p>
     */
    private static boolean isUsableSymbol(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return false;
        }
        return ticker.matches("^[A-Z0-9][A-Z0-9.:/-]{0,19}$");
    }

    /**
     * Factory method: Create empty context (for testing or when no data available).
     */
    public static MarketDataContext empty(Instant analysisTimestamp) {
        return new MarketDataContext(Map.of(), analysisTimestamp);
    }

    /**
     * Extract price from market event's raw data.
     */
    private Optional<BigDecimal> extractPrice(MarketEvent event) {
        try {
            Map<String, Object> rawData = event.rawData();
            if (rawData != null && rawData.containsKey("price")) {
                Object priceObj = rawData.get("price");
                if (priceObj instanceof Number) {
                    return Optional.of(new BigDecimal(priceObj.toString()));
                } else if (priceObj instanceof String) {
                    return Optional.of(new BigDecimal((String) priceObj));
                }
            }
        } catch (Exception e) {
            // Silently ignore parse errors
        }
        return Optional.empty();
    }

    /**
     * Format a news event as a readable headline.
     */
    private String formatNewsHeadline(MarketEvent event) {
        Map<String, Object> rawData = event.rawData();
        String title = null;

        if (rawData != null) {
            title = (String) rawData.getOrDefault("headline",
                    rawData.getOrDefault("title",
                    rawData.getOrDefault("description", null)));
        }

        String source = event.source() != null ? event.source() : event.symbol();
        String timestamp = event.timestamp().toString();

        return new StringBuilder()
            .append('[').append(timestamp, 0, Math.min(19, timestamp.length()))
            .append("] [").append(source)
            .append("] ").append(title != null ? title : "No headline available")
            .toString();
    }

    @Override
    public String toString() {
        return String.format(
            "MarketDataContext[symbols=%d, events=%d, timestamp=%s]",
            availableSymbols.size(),
            availableSymbols.stream().mapToInt(this::getEventCount).sum(),
            analysisTimestamp
        );
    }
}
