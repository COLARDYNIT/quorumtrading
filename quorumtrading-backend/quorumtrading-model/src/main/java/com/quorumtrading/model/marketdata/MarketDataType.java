package com.quorumtrading.model.marketdata;

/**
 * Enum representing different types of market data that can be collected and analyzed.
 */
public enum MarketDataType {
    /**
     * Price/quote data (OHLCV - Open, High, Low, Close, Volume)
     */
    PRICE,

    /**
     * News articles and press releases
     */
    NEWS,

    /**
     * Social media sentiment and discussions
     */
    SOCIAL,

    /**
     * On-chain blockchain metrics (for crypto)
     */
    ONCHAIN,

    /**
     * General market events (earnings, dividends, splits)
     */
    EVENT
}
