package com.quorumtrading.adapter.marketdata.finnhub;

import com.quorumtrading.adapter.marketdata.finnhub.dto.FinnhubNewsResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * REST client for Finnhub stock market data API.
 *
 * Free tier: 60 API calls/minute
 * API documentation: https://finnhub.io/docs/api
 */
@RegisterRestClient(configKey = "finnhub-api")
@Path("/api/v1")
public interface FinnhubRestClient {

    /**
     * Get real-time quote for a stock symbol.
     *
     * Response format:
     * {
     *   "c": 261.74,    // Current price
     *   "d": 0.32,      // Change
     *   "dp": 0.12,     // Percent change
     *   "h": 263.31,    // High price of the day
     *   "l": 260.68,    // Low price of the day
     *   "o": 261.07,    // Open price of the day
     *   "pc": 261.42,   // Previous close price
     *   "t": 1582641000 // Timestamp
     * }
     *
     * @param symbol Stock ticker symbol (e.g., "AAPL", "MSFT")
     * @param token API token (injected from config)
     * @return JSON response with quote data
     */
    @GET
    @Path("/quote")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getQuote(
        @QueryParam("symbol") String symbol,
        @QueryParam("token") String token
    );

    /**
     * Get historical OHLC candles for a stock symbol.
     *
     * Response format:
     * {
     *   "c": [217.68, 221.03, ...],  // Close prices
     *   "h": [222.49, 221.50, ...],  // High prices
     *   "l": [217.19, 217.10, ...],  // Low prices
     *   "o": [221.03, 218.55, ...],  // Open prices
     *   "s": "ok",                    // Status
     *   "t": [1569297600, ...],       // Timestamps (UNIX epoch)
     *   "v": [33463820, ...]          // Volumes
     * }
     *
     * Supported resolutions: 1, 5, 15, 30, 60, D, W, M
     * - 1 = 1 minute
     * - 5 = 5 minutes
     * - 15 = 15 minutes
     * - 30 = 30 minutes
     * - 60 = 1 hour
     * - D = 1 day
     * - W = 1 week
     * - M = 1 month
     *
     * @param symbol Stock ticker symbol (e.g., "AAPL", "TSLA")
     * @param resolution Candle resolution (e.g., "D" for daily, "60" for hourly)
     * @param from Start time (UNIX timestamp)
     * @param to End time (UNIX timestamp)
     * @param token API token (injected from config)
     * @return JSON response with candle data
     */
    @GET
    @Path("/stock/candle")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getCandles(
        @QueryParam("symbol") String symbol,
        @QueryParam("resolution") String resolution,
        @QueryParam("from") long from,
        @QueryParam("to") long to,
        @QueryParam("token") String token
    );

    /**
     * Get company news for a symbol within a date range.
     *
     * Response format: Array of news objects with headline, summary, source, url, etc.
     *
     * @param symbol Stock ticker symbol (e.g., "AAPL", "MSFT")
     * @param from Start date (YYYY-MM-DD format)
     * @param to End date (YYYY-MM-DD format)
     * @param token API token (injected from config)
     * @return List of news items
     */
    @GET
    @Path("/company-news")
    @Produces(MediaType.APPLICATION_JSON)
    List<FinnhubNewsResponse> getCompanyNews(
        @QueryParam("symbol") String symbol,
        @QueryParam("from") String from,
        @QueryParam("to") String to,
        @QueryParam("token") String token
    );

    /**
     * Get general market news.
     *
     * Response format: Array of news objects with headline, summary, source, url, etc.
     *
     * @param category News category (default: "general", options: "forex", "crypto", "merger")
     * @param token API token (injected from config)
     * @return List of news items
     */
    @GET
    @Path("/news")
    @Produces(MediaType.APPLICATION_JSON)
    List<FinnhubNewsResponse> getMarketNews(
        @QueryParam("category") String category,
        @QueryParam("token") String token
    );

    /**
     * Get financial metrics (valuation ratios, growth, profitability, etc.).
     * Returns 100+ metrics when metric=all.
     * Only returns data for individual stocks (not ETFs or crypto).
     */
    @GET
    @Path("/stock/metric")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getStockMetrics(
        @QueryParam("symbol") String symbol,
        @QueryParam("metric") String metric,
        @QueryParam("token") String token
    );

    /**
     * Get company profile (name, industry, market cap, shares outstanding).
     * Returns empty {} for ETFs.
     */
    @GET
    @Path("/stock/profile2")
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, Object> getCompanyProfile(
        @QueryParam("symbol") String symbol,
        @QueryParam("token") String token
    );

    /**
     * Get earnings history (actual vs estimate EPS per quarter).
     * Returns empty [] for ETFs.
     */
    @GET
    @Path("/stock/earnings")
    @Produces(MediaType.APPLICATION_JSON)
    List<Map<String, Object>> getEarnings(
        @QueryParam("symbol") String symbol,
        @QueryParam("token") String token
    );

    /**
     * Get analyst recommendation trends (buy/hold/sell counts per month).
     * Returns empty [] for ETFs.
     */
    @GET
    @Path("/stock/recommendation")
    @Produces(MediaType.APPLICATION_JSON)
    List<Map<String, Object>> getRecommendation(
        @QueryParam("symbol") String symbol,
        @QueryParam("token") String token
    );
}
