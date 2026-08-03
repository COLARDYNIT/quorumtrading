package com.quorumtrading.adapter.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * WebSocket message for market data events.
 * Matches frontend type: MarketEventMessage
 */
public class MarketEventMessage implements BaseStreamMessage {

    @JsonProperty("type")
    private final String type = "MARKET_EVENT";

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("simulationId")
    private String simulationId;

    @JsonProperty("eventId")
    private long eventId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("data")
    private Map<String, Object> data;

    public MarketEventMessage() {
    }

    public MarketEventMessage(String timestamp, String simulationId, long eventId,
                              String symbol, String eventType, Map<String, Object> data) {
        this.timestamp = timestamp;
        this.simulationId = simulationId;
        this.eventId = eventId;
        this.symbol = symbol;
        this.eventType = eventType;
        this.data = data;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(String simulationId) {
        this.simulationId = simulationId;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
