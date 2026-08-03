package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoInstrumentDetails;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoInstrumentSearchResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Saxo Bank OpenAPI <b>Reference Data</b> REST client (the {@code /ref/v1}
 * surface: instrument search + instrument details for {@code Symbol → Uic +
 * AssetType} resolution).
 *
 * <p><b>Scaffold (SB1).</b> Split from {@link SaxoTradingClient} onto its own
 * {@code saxo-ref} config key because reference data is read-only and may point at
 * a different base path/host than the trade gateway; both still share the OAuth
 * bearer via {@link SaxoAuthHeadersFactory}. The {@code Symbol → Uic} resolver that
 * consumes these calls is {@code SB3-uic-resolver}; no resolution logic lives
 * here.</p>
 *
 * <p><b>SB3-uic-resolver.</b> The search now binds {@code Keywords}, {@code AssetTypes}
 * and {@code ExchangeId} and deserialises into the {@code Data}-wrapped
 * {@link SaxoInstrumentSearchResponse} envelope (SAXO_ANSWERS §2.3); the details call
 * stays a single-object projection. The {@code SaxoUicResolver} that consumes these
 * calls reuses the model {@code SymbolParser} for MIC/currency mapping — it does not
 * re-roll tables.</p>
 *
 * <p><b>⚠️ TODO(SB3, unconfirmed):</b> Saxo's {@code ExchangeId} query value is its own
 * internal exchange code, which is <em>usually</em> but not guaranteed to equal the ISO
 * MIC; exotic venues are TODO-flagged in {@code SaxoExchangeIdMapper}.</p>
 *
 * <p>Config keys this client needs ({@code CFG1} supplies, default OFF / placeholder):</p>
 * <ul>
 *   <li>{@code quarkus.rest-client.saxo-ref.url} — Saxo OpenApi base URL (SIM/LIVE per env)</li>
 * </ul>
 */
@RegisterRestClient(configKey = "saxo-ref")
@RegisterClientHeaders(SaxoAuthHeadersFactory.class)
@Path("/ref/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SaxoReferenceDataClient {

    /**
     * Keyword instrument search, pinned to a venue (Saxo {@code ExchangeId}) and asset type.
     * {@code GET /ref/v1/instruments?Keywords=…&AssetTypes=…&ExchangeId=…}
     *
     * <p>Results arrive wrapped in a {@code Data} array ({@link SaxoInstrumentSearchResponse}).
     * The {@code AssetTypes} param is plural and comma-separated at the Saxo API; the resolver
     * passes a single value ({@code Stock} / {@code Etf}) per call to keep the Etf→Stock
     * fallback explicit. A blank {@code exchangeId} (no venue pin) is sent as {@code null} so
     * the param is omitted (US / unqualified search).</p>
     *
     * @param keywords   the instrument keyword (ticker root or name)
     * @param assetTypes the Saxo asset type to constrain to ({@code Stock} / {@code Etf})
     * @param exchangeId the Saxo exchange id to pin the venue, or {@code null} to omit
     */
    @GET
    @Path("/instruments")
    SaxoInstrumentSearchResponse searchInstruments(@QueryParam("Keywords") String keywords,
                                                   @QueryParam("AssetTypes") String assetTypes,
                                                   @QueryParam("ExchangeId") String exchangeId);

    /**
     * Full instrument details for a Uic + AssetType: {@code IsTradable}, {@code CurrencyCode},
     * {@code PrimaryListing}, and the per-listing trading constraints ({@code LotSize},
     * {@code MinimumTradeSize}, {@code MinimumOrderValue}, {@code AmountDecimals},
     * {@code TickSize}).
     * {@code GET /ref/v1/instruments/details/{uic}/{assetType}}
     *
     * <p><b>⚠️ TODO(SB3, unconfirmed):</b> the details path + single-object envelope are
     * best-effort against SAXO_ANSWERS §2.2/§2.3; a 404 here is treated as "not found on this
     * venue" by the resolver (drives the Etf→Stock fallback).</p>
     */
    @GET
    @Path("/instruments/details/{uic}/{assetType}")
    SaxoInstrumentDetails getInstrumentDetails(@PathParam("uic") Long uic,
                                               @PathParam("assetType") String assetType);
}
