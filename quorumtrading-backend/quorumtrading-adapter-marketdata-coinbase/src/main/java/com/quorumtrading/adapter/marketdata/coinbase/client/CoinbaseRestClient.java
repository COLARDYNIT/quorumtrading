package com.quorumtrading.adapter.marketdata.coinbase.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Keyless Coinbase Exchange public candles endpoint. No authentication (OpenAPI {@code security: []}).
 * A {@code User-Agent} header is required or the edge rejects the request.
 *
 * <p>Docs: <a href="https://docs.cdp.coinbase.com/api-reference/exchange-api/rest-api/products/get-product-candles">get-product-candles</a>.
 * Returns at most 300 candles per request (a wider range yields HTTP 400), so the adapter paginates
 * by walking the {@code start}/{@code end} window backward. Response is a JSON array of
 * {@code [time, low, high, open, close, volume]} — note the low/high before open/close ordering.</p>
 */
@RegisterRestClient(configKey = "coinbase-api")
@ClientHeaderParam(name = "User-Agent", value = "quorumtrading/1.0 (+https://quorumtrading.com)")
public interface CoinbaseRestClient {

    /**
     * @param productId   e.g. {@code BTC-USD}
     * @param granularity candle size in seconds: 60,300,900,3600,21600,86400
     * @param startIso    ISO-8601 start (inclusive)
     * @param endIso      ISO-8601 end (inclusive)
     * @return raw JSON array body
     */
    @GET
    @Path("/products/{productId}/candles")
    @Produces(MediaType.APPLICATION_JSON)
    String getCandles(@PathParam("productId") String productId,
                      @QueryParam("granularity") int granularity,
                      @QueryParam("start") String startIso,
                      @QueryParam("end") String endIso);
}
