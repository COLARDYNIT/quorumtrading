package com.quorumtrading.adapter.rest.dto.response;

import java.util.Map;

/**
 * Market event response DTO matching frontend expectations.
 * Maps from application layer MarketEvent.
 */
public class MarketEventResponse {

    public String id;
    public String timestamp;
    public String eventType;
    public String source;
    public String symbol;
    public Map<String, Object> rawData;
    public String ingestedAt;

    public MarketEventResponse() {}

    public MarketEventResponse(String id, String timestamp, String eventType, String source,
                               String symbol, Map<String, Object> rawData, String ingestedAt) {
        this.id = id;
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.source = source;
        this.symbol = symbol;
        this.rawData = rawData;
        this.ingestedAt = ingestedAt;
    }
}
