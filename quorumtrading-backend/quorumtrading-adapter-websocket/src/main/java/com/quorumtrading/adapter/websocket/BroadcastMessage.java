package com.quorumtrading.adapter.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * Message format for broadcasting events to WebSocket clients.
 * Serializes to JSON format expected by clients.
 */
public class BroadcastMessage {

    @JsonProperty("channel")
    private final String channel;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("data")
    private final Map<String, Object> data;

    public BroadcastMessage(String channel, Instant timestamp, Map<String, Object> data) {
        this.channel = channel;
        this.timestamp = timestamp;
        this.data = data;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
