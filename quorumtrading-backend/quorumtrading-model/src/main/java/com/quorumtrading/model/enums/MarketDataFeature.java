package com.quorumtrading.model.enums;

/**
 * Features supported by market data providers.
 * Used for capability-based provider selection in backfill and data fetching operations.
 *
 * <p>Feature Categories:</p>
 * <ul>
 *   <li>Asset Types: CRYPTO, STOCK</li>
 *   <li>Data Types: PRICE, NEWS, SOCIAL_MEDIA</li>
 *   <li>Capabilities: BACKFILL, REAL_TIME, CANDLES, INTRADAY, DAILY</li>
 *   <li>Quality: LIVE_TRADING, RESEARCH</li>
 * </ul>
 */
public enum MarketDataFeature {
    /**
     * Supports cryptocurrency data (Bitcoin, Ethereum, etc.)
     */
    CRYPTO,

    /**
     * Supports stock market data (AAPL, GOOGL, etc.)
     */
    STOCK,

    /**
     * Provides news articles and market news
     */
    NEWS,

    /**
     * Provides social media data (Reddit posts, Twitter sentiment, etc.)
     */
    SOCIAL_MEDIA,

    /**
     * Supports historical data backfilling for date ranges
     */
    BACKFILL,

    /**
     * Supports real-time streaming data (WebSocket, live feeds)
     */
    REAL_TIME,

    /**
     * Provides OHLCV candle/bar data
     */
    CANDLES,

    /**
     * Supports intraday data (minute-level granularity)
     */
    INTRADAY,

    /**
     * Supports daily aggregated data
     */
    DAILY,

    /**
     * Suitable for live trading (reliable, low latency, official exchange data)
     */
    LIVE_TRADING,

    /**
     * Suitable for research and backtesting (may have delays, free tier limitations)
     */
    RESEARCH,

    /**
     * Covers European-listed instruments — priced from the venue/currency/clock they
     * trade on (e.g. a Saxo EU exchange feed) rather than a US-default source.
     */
    EU_INSTRUMENT
}
