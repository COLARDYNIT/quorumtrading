package com.quorumtrading.adapter.marketdata.saxo.client;

import com.quorumtrading.adapter.marketdata.saxo.client.dto.ChartsResponse;
import com.quorumtrading.adapter.marketdata.saxo.client.dto.InfoPricesResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Saxo Bank OpenAPI <b>market-data</b> REST client — the price surface used to value EU
 * instruments from the same venue/currency/clock they trade on.
 *
 * <p><b>Scaffold (MD1).</b> Covers the two endpoints the Saxo market-data adapter needs:</p>
 * <ul>
 *   <li>{@code GET /trade/v1/infoprices} — live (or delayed) quotes for a single Uic
 *       ({@code MD2-live-quotes}).</li>
 *   <li>{@code GET /chart/v1/charts} — historical OHLC samples for backfill, windowed by
 *       {@code Count}/{@code Mode}/{@code Time} ({@code MD3-charts-backfill}).</li>
 * </ul>
 *
 * <p>Base URL is the Saxo OpenApi gateway — SIM
 * ({@code https://gateway.saxobank.com/sim/openapi}) or LIVE
 * ({@code https://gateway.saxobank.com/openapi}) — supplied by {@code CFG1} via the
 * {@code saxo-openapi} REST-client config key
 * ({@code quarkus.rest-client.saxo-openapi.url}). Auth is a per-account OAuth2 bearer added
 * by {@link SaxoAuthHeadersFactory}.</p>
 *
 * <p><b>TODO(MD2/MD3):</b> the query-parameter names below follow the published Saxo
 * OpenAPI shapes ({@code Uic}, {@code AssetType}, {@code Horizon}, {@code Count},
 * {@code Mode}, {@code Time}, {@code FieldGroups}) but must be reconfirmed against the live
 * spec and our market-data entitlements before the adapter logic is wired. No business logic
 * lives here — this is the typed HTTP surface only (WP boundary: client + auth + DTOs;
 * the {@code MarketDataPort} impl is MD2/MD3/MD4).</p>
 *
 * <p>Config keys this client needs ({@code CFG1} supplies; default placeholder / OFF):</p>
 * <ul>
 *   <li>{@code quarkus.rest-client.saxo-openapi.url} — Saxo OpenApi base URL (SIM/LIVE per env)</li>
 * </ul>
 */
@RegisterRestClient(configKey = "saxo-openapi")
@RegisterClientHeaders(SaxoAuthHeadersFactory.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SaxoOpenApiClient {

    /**
     * Get live (or exchange-delayed) info-prices for a single instrument.
     *
     * <p>{@code GET /trade/v1/infoprices?Uic={uic}&AssetType={assetType}&FieldGroups={fieldGroups}}</p>
     *
     * <p>TODO(MD2): confirm {@code FieldGroups} value(s) needed to obtain the
     * {@code Quote}/{@code PriceInfo} (and the {@code DelayedByMinutes}/market-state flags)
     * and whether intraday O/H/L is available here or only via Charts.</p>
     *
     * @param uic        Saxo instrument identifier (numeric Uic)
     * @param assetType  Saxo asset type (e.g. {@code Stock}, {@code Etf})
     * @param fieldGroups comma-separated field groups (e.g. {@code Quote,PriceInfoDetails})
     * @return the info-prices payload for the requested instrument
     */
    @GET
    @Path("/trade/v1/infoprices")
    InfoPricesResponse getInfoPrices(@QueryParam("Uic") long uic,
                                     @QueryParam("AssetType") String assetType,
                                     @QueryParam("FieldGroups") String fieldGroups);

    /**
     * Get historical OHLC chart samples for an instrument.
     *
     * <p>{@code GET /chart/v1/charts?Uic={uic}&AssetType={assetType}&Horizon={horizon}
     * &Count={count}&Mode={mode}&Time={time}}</p>
     *
     * <p>TODO(MD3): confirm pagination semantics — {@code Count} cap, {@code Mode}
     * ({@code From}/{@code UpTo}) + {@code Time} anchor — and that {@code Horizon} is the
     * minutes-per-sample granularity (1440 = daily).</p>
     *
     * @param uic       Saxo instrument identifier (numeric Uic)
     * @param assetType Saxo asset type (e.g. {@code Stock}, {@code Etf})
     * @param horizon   chart horizon in minutes per sample (e.g. {@code 1440} for daily)
     * @param count     number of samples to return (subject to the Saxo Count cap)
     * @param mode      pagination mode anchored at {@code time} (e.g. {@code From}, {@code UpTo})
     * @param time      ISO-8601 anchor timestamp for the {@code mode} window (nullable)
     * @return the chart-samples payload for the requested instrument and window
     */
    @GET
    @Path("/chart/v1/charts")
    ChartsResponse getCharts(@QueryParam("Uic") long uic,
                             @QueryParam("AssetType") String assetType,
                             @QueryParam("Horizon") int horizon,
                             @QueryParam("Count") int count,
                             @QueryParam("Mode") String mode,
                             @QueryParam("Time") String time);
}
