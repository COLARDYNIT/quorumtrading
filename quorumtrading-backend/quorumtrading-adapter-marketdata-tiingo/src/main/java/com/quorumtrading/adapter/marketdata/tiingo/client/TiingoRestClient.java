package com.quorumtrading.adapter.marketdata.tiingo.client;

import com.quorumtrading.adapter.marketdata.tiingo.client.dto.TiingoPriceBar;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Tiingo Daily Price API REST client.
 *
 * Base URL: https://api.tiingo.com
 * Authentication: Token-based via Authorization header (configured via ClientHeadersFactory)
 *
 * Tiingo provides 30+ years of split+dividend adjusted daily stock data.
 * Single ticker per request (unlike Alpaca's multi-symbol support).
 */
@RegisterRestClient(configKey = "tiingo-api")
@RegisterClientHeaders(TiingoAuthHeadersFactory.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface TiingoRestClient {

    /**
     * Get daily price bars for a single ticker within a date range.
     * GET /tiingo/daily/{ticker}/prices
     *
     * @param ticker       Stock ticker symbol (e.g., "AAPL", "QQQ")
     * @param startDate    Start date in YYYY-MM-DD format
     * @param endDate      End date in YYYY-MM-DD format
     * @param resampleFreq Frequency: "daily", "weekly", or "monthly"
     * @return List of price bars for the requested date range
     */
    @GET
    @Path("/tiingo/daily/{ticker}/prices")
    List<TiingoPriceBar> getDailyPrices(@PathParam("ticker") String ticker,
                                         @QueryParam("startDate") String startDate,
                                         @QueryParam("endDate") String endDate,
                                         @QueryParam("resampleFreq") String resampleFreq);
}
