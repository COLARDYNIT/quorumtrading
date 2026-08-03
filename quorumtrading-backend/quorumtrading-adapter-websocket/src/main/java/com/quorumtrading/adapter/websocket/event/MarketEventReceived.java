package com.quorumtrading.adapter.websocket.event;

import java.time.Instant;
import java.util.Map;

/**
 * CDI event fired when new market data is received.
 * Observed by MessageBroadcaster to broadcast to market.events channel subscribers.
 */
public class MarketEventReceived {

    private final String symbol;
    private final String eventType;
    private final Instant timestamp;
    private final Map<String, Object> data;

    public MarketEventReceived(String symbol, String eventType, Instant timestamp, Map<String, Object> data) {
        this.symbol = symbol;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.data = data;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
