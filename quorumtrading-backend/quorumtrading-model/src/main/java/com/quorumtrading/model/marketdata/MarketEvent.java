package com.quorumtrading.model.marketdata;

import com.quorumtrading.model.enums.EventType;

import java.time.Instant;
import java.util.Map;

/**
 * Domain entity representing a market event.
 * Market events include price updates, news, social media sentiment, etc.
 */
public record MarketEvent(
    Long id,
    Instant timestamp,
    EventType eventType,
    String source,
    String symbol,
    Map<String, Object> rawData,
    Instant ingestedAt,
    Instant cachedAt,
    String contentHash
) {
    /**
     * Constructor for creating new market events (without id).
     */
    public MarketEvent(
        Instant timestamp,
        EventType eventType,
        String source,
        String symbol,
        Map<String, Object> rawData
    ) {
        this(null, timestamp, eventType, source, symbol, rawData, Instant.now(), null, null);
    }
}
