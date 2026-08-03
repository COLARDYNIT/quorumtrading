package com.quorumtrading.adapter.marketdata.saxo.client;

import com.quorumtrading.adapter.marketdata.saxo.client.dto.SaxoMdInstrumentSearchResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Saxo Bank OpenAPI <b>Reference Data</b> REST client for the market-data adapter — the
 * {@code /ref/v1/instruments} search used to turn a {@link com.quorumtrading.model.valueobject.Symbol}
 * (ticker + MIC) into the {@code (Uic, AssetType)} identity an InfoPrices/Charts call needs.
 *
 * <p><b>MD2-live-quotes — the module's OWN reference-data client.</b> Adapters must not depend on
 * each other (hexagonal layering, ArchUnit-enforced), so the market-data module carries its own
 * reference-data client + DTOs rather than importing the broker-saxo {@code SaxoReferenceDataClient}.
 * It registers under a <b>distinct</b> {@code saxo-md-ref} config key so the two Saxo modules'
 * rest-clients stay independent. Auth is the same per-account OAuth2 bearer added by
 * {@link SaxoAuthHeadersFactory}.</p>
 *
 * <p>The market-data live path only needs the {@code (Uic, AssetType)} pair, so this client
 * exposes the search endpoint alone (no instrument-details call — the order-sizing constraints
 * the broker resolver loads are not relevant to pricing).</p>
 *
 * <p>Config keys this client needs (CFG owns the real values; default placeholder / OFF):</p>
 * <ul>
 *   <li>{@code quarkus.rest-client.saxo-md-ref.url} — Saxo OpenApi base URL (LIVE for equity data)</li>
 * </ul>
 */
@RegisterRestClient(configKey = "saxo-md-ref")
@RegisterClientHeaders(SaxoAuthHeadersFactory.class)
@Path("/ref/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SaxoMdReferenceDataClient {

    /**
     * Keyword instrument search, pinned to a venue (Saxo {@code ExchangeId}) and asset type.
     * {@code GET /ref/v1/instruments?Keywords=…&AssetTypes=…&ExchangeId=…}
     *
     * <p>Results arrive wrapped in a {@code Data} array
     * ({@link SaxoMdInstrumentSearchResponse}). The resolver passes a single
     * {@code AssetTypes} value ({@code Stock} / {@code Etf}) per call to keep the Etf→Stock
     * fallback explicit. A blank {@code exchangeId} is sent as {@code null} so the param is
     * omitted (unqualified search).</p>
     *
     * @param keywords   the instrument keyword (ticker root or name)
     * @param assetTypes the Saxo asset type to constrain to ({@code Stock} / {@code Etf})
     * @param exchangeId the Saxo exchange id to pin the venue, or {@code null} to omit
     */
    @GET
    @Path("/instruments")
    SaxoMdInstrumentSearchResponse searchInstruments(@QueryParam("Keywords") String keywords,
                                                     @QueryParam("AssetTypes") String assetTypes,
                                                     @QueryParam("ExchangeId") String exchangeId);
}
