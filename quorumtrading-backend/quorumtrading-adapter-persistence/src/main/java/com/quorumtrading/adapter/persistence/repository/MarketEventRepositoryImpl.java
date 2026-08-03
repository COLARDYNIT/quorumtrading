package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.NewsEventJpaEntity;
import com.quorumtrading.adapter.persistence.entity.PriceBarJpaEntity;
import com.quorumtrading.adapter.persistence.entity.SocialEventJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.NewsEventPersistenceMapper;
import com.quorumtrading.adapter.persistence.mapper.PriceBarPersistenceMapper;
import com.quorumtrading.adapter.persistence.mapper.SocialEventPersistenceMapper;
import com.quorumtrading.application.port.out.MarketEventRepository;
import com.quorumtrading.model.enums.EventType;
import com.quorumtrading.model.marketdata.MarketEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Repository adapter for MarketEvent.
 * Writes to typed tables (price_bars, news_events, social_events) and reads from them.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class MarketEventRepositoryImpl implements MarketEventRepository {

    private static final Logger logger = LoggerFactory.getLogger(MarketEventRepositoryImpl.class);

    @Inject
    PriceBarPersistenceMapper priceBarMapper;

    @Inject
    NewsEventPersistenceMapper newsEventMapper;

    @Inject
    SocialEventPersistenceMapper socialEventMapper;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public MarketEvent save(MarketEvent event) {
        // Generate content hash for deduplication
        String contentHash = null;
        try {
            String stableContent = extractStableContent(event);
            contentHash = generateContentHash(stableContent);
        } catch (Exception e) {
            logger.warn("Failed to generate content hash, hash will be null - event will proceed without deduplication protection", e);
        }

        // Check for existing event with same content hash BEFORE attempting insert
        if (contentHash != null && isDuplicate(contentHash, event.eventType())) {
            logger.debug("Skipping duplicate market event: {} {} at {}",
                    event.eventType(), event.symbol(), event.timestamp());
            return null;
        }

        try {
            MarketEvent saved = persistToTypedTable(event, contentHash);
            entityManager.flush();
            return saved;
        } catch (jakarta.persistence.PersistenceException e) {
            if (isDuplicateKeyError(e)) {
                logger.debug("Skipping duplicate market event (race condition): {} {} at {} - contentHash: {}",
                        event.eventType(), event.symbol(), event.timestamp(), contentHash);
                return null;
            }
            throw e;
        } catch (Exception e) {
            if (isDuplicateKeyError(e)) {
                logger.debug("Skipping duplicate market event (race condition): {} {} at {} - contentHash: {}",
                        event.eventType(), event.symbol(), event.timestamp(), contentHash);
                return null;
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public MarketEvent saveLatestPriceQuote(MarketEvent event) {
        if (event.eventType() != EventType.PRICE) {
            return save(event);
        }
        // Replace-in-place per (symbol, day): drop earlier intraday quote rows for the same
        // calendar day before inserting the fresh quote. The strict '>' keeps the canonical
        // midnight daily bar (from backfill) untouched. Note: an intentionally backfilled
        // sub-daily dataset for TODAY would also be cleared — the collector only ever runs
        // against the current day, so historical intraday backfills are never affected.
        java.time.Instant dayStart = event.timestamp().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        java.time.Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
        long removed = PriceBarJpaEntity.delete(
            "symbol = ?1 AND timestamp > ?2 AND timestamp < ?3",
            event.symbol(), dayStart, dayEnd);
        if (removed > 0) {
            logger.debug("Replaced {} intraday quote row(s) for {} on {}", removed, event.symbol(), dayStart);
        }
        return save(event);
    }

    /**
     * Persist a MarketEvent to the appropriate typed table.
     */
    private MarketEvent persistToTypedTable(MarketEvent event, String contentHash) {
        return switch (event.eventType()) {
            case PRICE -> {
                PriceBarJpaEntity entity = priceBarMapper.toJpaEntity(event, contentHash);
                entityManager.persist(entity);
                yield priceBarMapper.toMarketEvent(entity);
            }
            case NEWS -> {
                NewsEventJpaEntity entity = newsEventMapper.toJpaEntity(event, contentHash);
                entityManager.persist(entity);
                yield newsEventMapper.toMarketEvent(entity);
            }
            case SOCIAL -> {
                SocialEventJpaEntity entity = socialEventMapper.toJpaEntity(event, contentHash);
                entityManager.persist(entity);
                yield socialEventMapper.toMarketEvent(entity);
            }
        };
    }

    /**
     * Check if a content hash already exists in the typed table for this event type.
     */
    private boolean isDuplicate(String contentHash, EventType eventType) {
        return switch (eventType) {
            case PRICE -> PriceBarJpaEntity.count("contentHash = ?1", contentHash) > 0;
            case NEWS -> NewsEventJpaEntity.count("contentHash = ?1", contentHash) > 0;
            case SOCIAL -> SocialEventJpaEntity.count("contentHash = ?1", contentHash) > 0;
        };
    }

    /**
     * Check if an exception is caused by a duplicate key constraint violation.
     */
    private boolean isDuplicateKeyError(Exception e) {
        if (e instanceof ConstraintViolationException) {
            return true;
        }
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof ConstraintViolationException ||
                (cause instanceof org.postgresql.util.PSQLException &&
                 cause.getMessage() != null &&
                 cause.getMessage().contains("duplicate key"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public List<MarketEvent> findByTickersAndTimeRange(Set<String> cryptoTickers, Set<String> stockTickers, Instant from, Instant to, EventType eventType, Integer limit) {
        // If specific event type requested, query typed table directly
        if (eventType != null) {
            return queryTypedTable(cryptoTickers, stockTickers, from, to, eventType, limit);
        }

        // Combine all tickers for per-symbol querying
        Set<String> allTickers = new HashSet<>();
        if (cryptoTickers != null && !cryptoTickers.isEmpty()) {
            allTickers.addAll(cryptoTickers);
        }
        if (stockTickers != null && !stockTickers.isEmpty()) {
            allTickers.addAll(stockTickers);
        }

        // Add MARKET symbol for general market news
        allTickers.add("MARKET");

        // Calculate limit per symbol to ensure balanced representation
        int requestedLimit = limit != null && limit > 0 ? limit : 50;
        int limitPerSymbol = Math.max(10, requestedLimit / Math.max(allTickers.size(), 1));

        logger.debug("Executing per-symbol queries with limit {} per symbol for {} tickers",
            limitPerSymbol, allTickers.size());

        List<MarketEvent> allEvents = new ArrayList<>();

        for (String ticker : allTickers) {
            Set<String> singleTickerSet = Set.of(ticker);

            // Preserve which universe this ticker was asked for. Flattening every symbol
            // into the CRYPTO argument with an empty STOCK set — as this loop did — makes
            // the PRICE query filter on `investmentType = 'CRYPTO' OR IS NULL`, and since
            // V127 stamps that column, every stock bar written after it was excluded. The
            // effect was invisible and expensive: agents received no price statistics for
            // equities at all, so the Technical agent reasoned without a close price and
            // inferred one from the Bollinger midline, then traded on the inference.
            // Crypto was unaffected, which is why it looked like an equities-only quirk.
            boolean isCrypto = cryptoTickers != null && cryptoTickers.contains(ticker);
            boolean isStock = stockTickers != null && stockTickers.contains(ticker);
            // A synthetic or unclassified symbol (e.g. MARKET) belongs to neither universe;
            // passing it as both selects the repository's documented "caller does not know
            // the asset class" path, which matches on symbol alone.
            Set<String> cryptoArg = isCrypto || (!isCrypto && !isStock) ? singleTickerSet : Set.of();
            Set<String> stockArg = isStock || (!isCrypto && !isStock) ? singleTickerSet : Set.of();

            for (EventType type : EventType.values()) {
                List<MarketEvent> eventsForSymbol = queryTypedTable(
                    cryptoArg, stockArg, from, to, type, limitPerSymbol
                );
                allEvents.addAll(eventsForSymbol);

                if (!eventsForSymbol.isEmpty()) {
                    logger.debug("Fetched {} events for symbol {} type {}",
                        eventsForSymbol.size(), ticker, type);
                }
            }
        }

        List<MarketEvent> sortedEvents = allEvents.stream()
            .sorted(Comparator.comparing(MarketEvent::timestamp).reversed())
            .distinct()
            .collect(Collectors.toList());

        logger.debug("Combined query returned {} total events across {} symbols",
            sortedEvents.size(), allTickers.size());
        return sortedEvents;
    }

    /**
     * Query typed tables based on event type.
     */
    private List<MarketEvent> queryTypedTable(
            Set<String> cryptoTickers,
            Set<String> stockTickers,
            Instant from,
            Instant to,
            EventType eventType,
            Integer limit) {

        Set<String> allTickers = new HashSet<>();
        if (cryptoTickers != null && !cryptoTickers.isEmpty()) {
            allTickers.addAll(cryptoTickers);
        }
        if (stockTickers != null && !stockTickers.isEmpty()) {
            allTickers.addAll(stockTickers);
        }

        int effectiveLimit = limit != null && limit > 0 ? limit : 100;

        return switch (eventType) {
            // PRICE keeps the two universes separate so a stock bar cannot answer a crypto
            // lookup; the other event types are not instrument-priced and use the union.
            case PRICE -> queryPriceBars(cryptoTickers, stockTickers, from, to, effectiveLimit);
            case NEWS -> queryNewsEvents(allTickers, from, to, effectiveLimit);
            case SOCIAL -> querySocialEvents(allTickers, from, to, effectiveLimit);
        };
    }

    /**
     * Query price bars, honouring which universe each ticker was asked for.
     *
     * <p>A ticker can exist as both an equity and a crypto, and {@code price_bars} keyed them
     * together. Callers that genuinely know the asset class (the mock broker pricing a fill, a
     * backtest valuing a position) pass disjoint sets and get only bars of the matching class;
     * callers that pass the same set as both — meaning "either, I don't know" — behave exactly
     * as before.</p>
     *
     * <p>Rows written before V127 have a null type and are always accepted: excluding them would
     * erase the entire price history from every existing simulation.</p>
     */
    private List<MarketEvent> queryPriceBars(Set<String> cryptoSymbols, Set<String> stockSymbols,
                                             Instant from, Instant to, int limit) {
        StringBuilder query = new StringBuilder();
        Parameters params = new Parameters();
        boolean first = true;

        boolean hasCrypto = cryptoSymbols != null && !cryptoSymbols.isEmpty();
        boolean hasStock = stockSymbols != null && !stockSymbols.isEmpty();

        if (hasCrypto && hasStock && cryptoSymbols.equals(stockSymbols)) {
            // Caller does not know the asset class — match on symbol alone, as before.
            query.append("symbol IN :symbols");
            params.and("symbols", cryptoSymbols);
            first = false;
        } else if (hasCrypto || hasStock) {
            query.append("(");
            if (hasCrypto) {
                query.append("(symbol IN :cryptoSymbols AND (investmentType = 'CRYPTO' OR investmentType IS NULL))");
                params.and("cryptoSymbols", cryptoSymbols);
            }
            if (hasCrypto && hasStock) {
                query.append(" OR ");
            }
            if (hasStock) {
                query.append("(symbol IN :stockSymbols AND (investmentType = 'STOCK' OR investmentType IS NULL))");
                params.and("stockSymbols", stockSymbols);
            }
            query.append(")");
            first = false;
        }

        if (from != null) {
            query.append(first ? "" : " AND ").append("timestamp >= :from");
            params.and("from", from);
            first = false;
        }
        if (to != null) {
            query.append(first ? "" : " AND ").append("timestamp <= :to");
            params.and("to", to);
        }
        query.append(" ORDER BY timestamp DESC");

        return PriceBarJpaEntity.find(query.toString(), params)
            .page(0, limit).list().stream()
            .map(e -> (PriceBarJpaEntity) e)
            .map(priceBarMapper::toMarketEvent)
            .collect(Collectors.toList());
    }

    private List<MarketEvent> queryNewsEvents(Set<String> symbols, Instant from, Instant to, int limit) {
        StringBuilder query = new StringBuilder();
        Parameters params = new Parameters();
        boolean first = true;

        if (!symbols.isEmpty()) {
            query.append("symbol IN :symbols");
            params.and("symbols", symbols);
            first = false;
        }
        if (from != null) {
            query.append(first ? "" : " AND ").append("timestamp >= :from");
            params.and("from", from);
            first = false;
        }
        if (to != null) {
            query.append(first ? "" : " AND ").append("timestamp <= :to");
            params.and("to", to);
        }
        query.append(" ORDER BY timestamp DESC");

        return NewsEventJpaEntity.find(query.toString(), params)
            .page(0, limit).list().stream()
            .map(e -> (NewsEventJpaEntity) e)
            .map(newsEventMapper::toMarketEvent)
            .collect(Collectors.toList());
    }

    private List<MarketEvent> querySocialEvents(Set<String> symbols, Instant from, Instant to, int limit) {
        StringBuilder query = new StringBuilder();
        Parameters params = new Parameters();
        boolean first = true;

        if (!symbols.isEmpty()) {
            query.append("symbol IN :symbols");
            params.and("symbols", symbols);
            first = false;
        }
        if (from != null) {
            query.append(first ? "" : " AND ").append("timestamp >= :from");
            params.and("from", from);
            first = false;
        }
        if (to != null) {
            query.append(first ? "" : " AND ").append("timestamp <= :to");
            params.and("to", to);
        }
        query.append(" ORDER BY timestamp DESC");

        return SocialEventJpaEntity.find(query.toString(), params)
            .page(0, limit).list().stream()
            .map(e -> (SocialEventJpaEntity) e)
            .map(socialEventMapper::toMarketEvent)
            .collect(Collectors.toList());
    }

    /**
     * Find cached market events by source pattern, symbol, and time range.
     * Routes to social_events (reddit) or news_events based on source pattern.
     */
    public List<MarketEvent> findBySourcePatternAndSymbolAndTimeRange(
            String sourcePattern,
            String symbol,
            Instant start,
            Instant end) {
        if (sourcePattern != null && sourcePattern.startsWith("reddit")) {
            return SocialEventJpaEntity.find(
                "source LIKE ?1 AND symbol = ?2 AND timestamp >= ?3 AND timestamp <= ?4 ORDER BY timestamp ASC",
                sourcePattern, symbol, start, end
            ).list().stream()
                .map(e -> (SocialEventJpaEntity) e)
                .map(socialEventMapper::toMarketEvent)
                .collect(Collectors.toList());
        }
        return NewsEventJpaEntity.find(
            "source LIKE ?1 AND symbol = ?2 AND timestamp >= ?3 AND timestamp <= ?4 ORDER BY timestamp ASC",
            sourcePattern, symbol, start, end
        ).list().stream()
            .map(e -> (NewsEventJpaEntity) e)
            .map(newsEventMapper::toMarketEvent)
            .collect(Collectors.toList());
    }

    /**
     * Delete old cached events for retention policy enforcement.
     */
    @Transactional
    public long deleteOlderThanCachedAt(Instant cutoffDate) {
        long count = PriceBarJpaEntity.delete("ingestedAt < ?1", cutoffDate);
        count += NewsEventJpaEntity.delete("ingestedAt < ?1", cutoffDate);
        count += SocialEventJpaEntity.delete("ingestedAt < ?1", cutoffDate);
        return count;
    }

    /**
     * Find all distinct content hashes within a time range.
     * Queries all three typed tables for complete deduplication coverage.
     */
    public Set<String> findContentHashesByTimeRange(Instant start, Instant end) {
        Set<String> hashes = new HashSet<>();

        PriceBarJpaEntity.find("timestamp >= ?1 AND timestamp <= ?2 AND contentHash IS NOT NULL", start, end)
            .stream()
            .map(e -> ((PriceBarJpaEntity) e).contentHash)
            .forEach(hashes::add);

        NewsEventJpaEntity.find("timestamp >= ?1 AND timestamp <= ?2 AND contentHash IS NOT NULL", start, end)
            .stream()
            .map(e -> ((NewsEventJpaEntity) e).contentHash)
            .forEach(hashes::add);

        SocialEventJpaEntity.find("timestamp >= ?1 AND timestamp <= ?2 AND contentHash IS NOT NULL", start, end)
            .stream()
            .map(e -> ((SocialEventJpaEntity) e).contentHash)
            .forEach(hashes::add);

        return hashes;
    }

    /**
     * Find market events by time range and symbols.
     * Queries all three typed tables and merges results by timestamp ASC.
     */
    @Override
    public List<MarketEvent> findByTimeRangeAndSymbols(com.quorumtrading.model.valueobject.TimeRange timeRange, Set<com.quorumtrading.model.valueobject.Symbol> symbols) {
        Set<String> symbolTickers = symbols.stream()
            .map(com.quorumtrading.model.valueobject.Symbol::ticker)
            .collect(Collectors.toSet());

        List<MarketEvent> allEvents = new ArrayList<>();

        PriceBarJpaEntity.find("symbol IN ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC",
            symbolTickers, timeRange.start(), timeRange.end())
            .stream()
            .map(e -> priceBarMapper.toMarketEvent((PriceBarJpaEntity) e))
            .forEach(allEvents::add);

        NewsEventJpaEntity.find("symbol IN ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC",
            symbolTickers, timeRange.start(), timeRange.end())
            .stream()
            .map(e -> newsEventMapper.toMarketEvent((NewsEventJpaEntity) e))
            .forEach(allEvents::add);

        SocialEventJpaEntity.find("symbol IN ?1 AND timestamp >= ?2 AND timestamp <= ?3 ORDER BY timestamp ASC",
            symbolTickers, timeRange.start(), timeRange.end())
            .stream()
            .map(e -> socialEventMapper.toMarketEvent((SocialEventJpaEntity) e))
            .forEach(allEvents::add);

        allEvents.sort(Comparator.comparing(MarketEvent::timestamp));
        return allEvents;
    }

    /**
     * Extract stable content from MarketEvent for hashing.
     */
    private String extractStableContent(MarketEvent event) throws JsonProcessingException {
        if (event.eventType() == EventType.PRICE) {
            return event.symbol() + "|" + event.timestamp().toString();
        }

        if (event.source().startsWith("reddit-")) {
            Object permalink = event.rawData().get("permalink");
            if (permalink != null) {
                return String.valueOf(permalink);
            }
        }

        if (event.source().startsWith("newsapi-")) {
            Object url = event.rawData().get("url");
            if (url != null) {
                return String.valueOf(url);
            }
        }

        if ("Finnhub".equals(event.source()) || event.rawData().containsKey("headline")) {
            Object url = event.rawData().get("url");
            if (url != null) {
                return String.valueOf(url);
            }
            Object headline = event.rawData().get("headline");
            if (headline != null) {
                return headline + "|" + event.timestamp().toString();
            }
        }

        if (event.rawData().containsKey("link") || event.rawData().containsKey("title")) {
            Object link = event.rawData().get("link");
            if (link != null) {
                return String.valueOf(link);
            }
            Object title = event.rawData().get("title");
            Object pubDate = event.rawData().get("pubDate");
            if (title != null && pubDate != null) {
                return title + "|" + pubDate.toString();
            }
        }

        return objectMapper.writeValueAsString(event.rawData());
    }

    /**
     * Generate SHA-256 hash of content for deduplication.
     */
    private String generateContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            logger.warn("Failed to generate SHA-256 hash, using fallback", e);
            return "hash-error-" + content.hashCode();
        }
    }

    /**
     * Market-event lookups key on {@link com.quorumtrading.model.valueobject.Symbol#canonical()},
     * never on the bare root.
     *
     * <p>The root is not an identity. {@code AZN} is AstraZeneca's New York
     * depositary receipt in dollars and {@code AZN:XLON} is its London ordinary
     * share in pounds, and querying the venue-qualified symbol by its root returns
     * the other instrument's bars. Because bars are stored under whatever symbol
     * was backfilled — in practice the US root, which is the only venue any
     * configured provider serves — the collision reads as success: a simulation
     * headed {@code AZN:XLON} was observed buying the NYSE receipt at a USD price
     * and reporting a completed London backtest.</p>
     *
     * <p>Keying on the canonical form makes a venue with no data return nothing,
     * which surfaces as a visible absence rather than as someone else's prices.
     * For US symbols {@code canonical()} is byte-identical to the bare ticker, so
     * every existing row, hash and query is unaffected.</p>
     */
    @Override
    public java.util.Optional<Instant> findEarliestPriceDate(com.quorumtrading.model.valueobject.Symbol symbol) {
        return PriceBarJpaEntity.find("symbol = ?1 ORDER BY timestamp ASC", symbol.canonical())
            .page(0, 1)
            .firstResultOptional()
            .map(e -> ((PriceBarJpaEntity) e).timestamp);
    }

    @Override
    public long countEventsInRange(com.quorumtrading.model.valueobject.Symbol symbol, EventType eventType, Instant startDate, Instant endDate) {
        return switch (eventType) {
            case PRICE -> PriceBarJpaEntity.count(
                "symbol = ?1 and timestamp >= ?2 and timestamp <= ?3",
                symbol.canonical(), startDate, endDate);
            case NEWS -> NewsEventJpaEntity.count(
                "symbol = ?1 and timestamp >= ?2 and timestamp <= ?3",
                symbol.canonical(), startDate, endDate);
            case SOCIAL -> SocialEventJpaEntity.count(
                "symbol = ?1 and timestamp >= ?2 and timestamp <= ?3",
                symbol.canonical(), startDate, endDate);
        };
    }
}
