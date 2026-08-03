package com.quorumtrading.adapter.marketdata.alpaca.client;

import com.quorumtrading.adapter.marketdata.alpaca.client.dto.AlpacaNewsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.CorporateActionsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.HistoricalBarsResponse;
import com.quorumtrading.adapter.marketdata.alpaca.client.dto.LatestBarsResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Alpaca Market Data API REST client.
 *
 * Based on Alpaca Market Data API OpenAPI specification.
 * Base URL: https://data.alpaca.markets
 *
 * Authentication: Requires APCA-API-KEY-ID and APCA-API-SECRET-KEY headers (configured via ClientHeadersFactory)
 *
 * Note: This client covers both stock and crypto market data endpoints.
 */
@RegisterRestClient(configKey = "alpaca-market-data")
@RegisterClientHeaders(AlpacaMarketDataAuthHeadersFactory.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AlpacaMarketDataClient {

    /**
     * Get latest stock bars (minute-level candlestick data).
     * GET /v2/stocks/bars/latest
     *
     * @param symbols Comma-separated list of stock symbols (e.g., "AAPL,TSLA")
     * @param feed Feed type (optional): sip, iex, otc. Default is sip.
     * @param currency Currency for pricing (optional). Default is USD.
     * @return Latest bars for requested symbols
     */
    @GET
    @Path("/v2/stocks/bars/latest")
    LatestBarsResponse getLatestStockBars(@QueryParam("symbols") String symbols,
                                          @QueryParam("feed") String feed,
                                          @QueryParam("currency") String currency);

    /**
     * Get latest crypto bars (minute-level candlestick data).
     * GET /v1beta3/crypto/{loc}/latest/bars
     *
     * @param location Exchange location: us (United States). Default is us.
     * @param symbols Comma-separated list of crypto symbols (e.g., "BTC/USD,ETH/USD")
     * @return Latest bars for requested crypto symbols
     */
    @GET
    @Path("/v1beta3/crypto/{loc}/latest/bars")
    LatestBarsResponse getLatestCryptoBars(@PathParam("loc") String location,
                                           @QueryParam("symbols") String symbols);

    /**
     * Get historical stock bars (candlestick data) for a time range.
     * GET /v2/stocks/bars
     *
     * @param symbols Comma-separated list of stock symbols (e.g., "AAPL,TSLA")
     * @param timeframe Timeframe for aggregation (e.g., "1Min", "5Min", "1Hour", "1Day")
     * @param start Start time in RFC3339 format (e.g., "2025-11-04T00:00:00Z")
     * @param end End time in RFC3339 format (e.g., "2025-11-04T23:59:59Z")
     * @param limit Maximum number of bars per symbol (max 10000, default 1000)
     * @param feed Feed type (optional): sip, iex, otc. Default is sip.
     * @param pageToken Pagination token for fetching next page (optional)
     * @param adjustment Price adjustment: raw, split, dividend, all. Default is raw.
     * @return Historical bars for requested symbols and time range
     */
    @GET
    @Path("/v2/stocks/bars")
    HistoricalBarsResponse getStockBars(@QueryParam("symbols") String symbols,
                                        @QueryParam("timeframe") String timeframe,
                                        @QueryParam("start") String start,
                                        @QueryParam("end") String end,
                                        @QueryParam("limit") Integer limit,
                                        @QueryParam("feed") String feed,
                                        @QueryParam("page_token") String pageToken,
                                        @QueryParam("adjustment") String adjustment);

    /**
     * Get historical crypto bars (candlestick data) for a time range.
     * GET /v1beta3/crypto/{loc}/bars
     *
     * @param location Exchange location: us (United States). Default is us.
     * @param symbols Comma-separated list of crypto symbols (e.g., "BTC/USD,ETH/USD")
     * @param timeframe Timeframe for aggregation (e.g., "1Min", "5Min", "1Hour", "1Day")
     * @param start Start time in RFC3339 format (e.g., "2025-11-04T00:00:00Z")
     * @param end End time in RFC3339 format (e.g., "2025-11-04T23:59:59Z")
     * @param limit Maximum number of bars per symbol (max 10000, default 1000)
     * @param pageToken Pagination token for fetching next page (optional)
     * @return Historical bars for requested crypto symbols and time range
     */
    @GET
    @Path("/v1beta3/crypto/{loc}/bars")
    HistoricalBarsResponse getCryptoBars(@PathParam("loc") String location,
                                         @QueryParam("symbols") String symbols,
                                         @QueryParam("timeframe") String timeframe,
                                         @QueryParam("start") String start,
                                         @QueryParam("end") String end,
                                         @QueryParam("limit") Integer limit,
                                         @QueryParam("page_token") String pageToken);

    /**
     * Get news articles for symbols within a date range.
     * GET /v1beta1/news
     *
     * Free with any Alpaca account. Historical data back to 2015. Sourced from Benzinga.
     *
     * @param symbols Comma-separated list of symbols (e.g., "AAPL,TSLA,BTCUSD")
     * @param start Start time in RFC3339 format (e.g., "2025-01-01T00:00:00Z")
     * @param end End time in RFC3339 format (e.g., "2025-01-31T23:59:59Z")
     * @param limit Max articles per page (1-50, default 10)
     * @param includeContent Include full article content (may contain HTML)
     * @param excludeContentless Exclude articles without content
     * @param pageToken Pagination token for fetching next page (optional)
     * @return Paginated news articles for requested symbols and time range
     */
    @GET
    @Path("/v1beta1/news")
    AlpacaNewsResponse getNews(@QueryParam("symbols") String symbols,
                               @QueryParam("start") String start,
                               @QueryParam("end") String end,
                               @QueryParam("limit") Integer limit,
                               @QueryParam("include_content") Boolean includeContent,
                               @QueryParam("exclude_contentless") Boolean excludeContentless,
                               @QueryParam("page_token") String pageToken);

    /**
     * Get corporate actions (dividends, splits, etc.) for symbols within a date range.
     * GET /v1/corporate-actions
     *
     * @param symbols   Comma-separated list of stock symbols (e.g., "AAPL,MSFT")
     * @param types     Comma-separated types to filter (e.g., "cash_dividend")
     * @param dateFrom  Start date in YYYY-MM-DD format
     * @param dateTo    End date in YYYY-MM-DD format
     * @param pageToken Pagination token for fetching next page (optional)
     * @return Corporate actions matching the query
     */
    @GET
    @Path("/v1/corporate-actions")
    CorporateActionsResponse getCorporateActions(@QueryParam("symbols") String symbols,
                                                  @QueryParam("types") String types,
                                                  @QueryParam("date_from") String dateFrom,
                                                  @QueryParam("date_to") String dateTo,
                                                  @QueryParam("page_token") String pageToken);
}
