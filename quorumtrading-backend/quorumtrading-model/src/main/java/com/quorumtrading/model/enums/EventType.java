package com.quorumtrading.model.enums;

/**
 * Enum representing different types of market events and data.
 * Used for categorizing market data, events, and analysis inputs.
 */
public enum EventType {
    /**
     * Price/quote data (OHLCV - Open, High, Low, Close, Volume)
     */
    PRICE("Price Update"),

    /**
     * News articles and press releases
     */
    NEWS("News Article"),

    /**
     * Social media sentiment and discussions (Reddit, Twitter, etc.)
     */
    SOCIAL("Social Media");

    private final String displayName;

    EventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
