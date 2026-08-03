package com.quorumtrading.adapter.marketdata.kraken.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Keyless Kraken public OHLC endpoint. No authentication — public market data only.
 *
 * <p>Docs: <a href="https://docs.kraken.com/api/docs/rest-api/get-ohlc-data">get-ohlc-data</a>.
 * Returns up to ~720 candles per interval ending "now"; {@code since} filters the start but cannot
 * page further back than that window. The {@code result} object is keyed by Kraken's canonical pair
 * name (e.g. {@code XXBTZUSD}); the adapter parses the raw JSON body.</p>
 */
@RegisterRestClient(configKey = "kraken-api")
public interface KrakenRestClient {

    /**
     * @param pair     Kraken pair, e.g. {@code XBTUSD} (Kraken uses {@code XBT} for Bitcoin)
     * @param interval candle size in minutes: 1,5,15,30,60,240,1440,10080,21600
     * @param since    Unix timestamp (seconds) to return candles from
     * @return raw JSON response body
     */
    @GET
    @Path("/0/public/OHLC")
    @Produces(MediaType.APPLICATION_JSON)
    String getOhlc(@QueryParam("pair") String pair,
                   @QueryParam("interval") int interval,
                   @QueryParam("since") long since);
}
