package com.quorumtrading.adapter.broker.saxo.client;

import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoAccountResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoBalanceResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoOpenOrdersResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoOrderRequest;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoOrderResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoPositionsResponse;
import com.quorumtrading.adapter.broker.saxo.client.dto.SaxoUserResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Saxo Bank OpenAPI <b>trade/account</b> REST client (the {@code /port/v1}
 * surface: users, accounts, balances, positions, orders).
 *
 * <p><b>Scaffold (SB1).</b> Modelled on the Alpaca trading client. Base URL is the
 * Saxo OpenApi gateway — SIM ({@code https://gateway.saxobank.com/sim/openapi}) or
 * LIVE ({@code https://gateway.saxobank.com/openapi}) — supplied by {@code CFG1}
 * via the {@code saxo-trading} REST-client config key. Auth is a per-account OAuth2
 * bearer added by {@link SaxoAuthHeadersFactory} (TODO(SB2): per-account token
 * resolution).</p>
 *
 * <p><b>TODO(SB2/SB4/SB5):</b> the {@code @Path}s below are best-effort against the
 * Saxo OpenAPI docs and must be confirmed before the adapter logic is wired —
 * Saxo's service groups live under {@code /port/v1/...} and {@code /ref/v1/...}
 * but the exact path segments, the {@code FieldGroups}/{@code AccountKey} query
 * params, and whether {@code /me} convenience variants exist all need verifying.
 * No business logic lives here — this is the typed HTTP surface only.</p>
 *
 * <p>Config keys this client needs ({@code CFG1} supplies, default OFF / placeholder):</p>
 * <ul>
 *   <li>{@code quarkus.rest-client.saxo-trading.url} — Saxo OpenApi base URL (SIM/LIVE per env)</li>
 * </ul>
 */
@RegisterRestClient(configKey = "saxo-trading")
@RegisterClientHeaders(SaxoAuthHeadersFactory.class)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SaxoTradingClient {

    /**
     * Get the current user (resolves ClientKey/UserKey).
     * {@code GET /port/v1/users/me}
     * TODO(SB2): confirm path + response envelope.
     */
    @GET
    @Path("/port/v1/users/me")
    SaxoUserResponse getCurrentUser();

    /**
     * Get the caller's accounts (resolves AccountKey + settlement currency).
     * {@code GET /port/v1/accounts/me}
     * TODO(SB2): confirm path + that accounts are wrapped in a {@code Data} array.
     */
    @GET
    @Path("/port/v1/accounts/me")
    SaxoAccountResponse getAccounts();

    /**
     * Get account balance for a specific AccountKey.
     * {@code GET /port/v1/balances?AccountKey=…&ClientKey=…}
     * TODO(SB5): confirm path + required query params.
     */
    @GET
    @Path("/port/v1/balances")
    SaxoBalanceResponse getBalance(@QueryParam("AccountKey") String accountKey,
                                   @QueryParam("ClientKey") String clientKey);

    /**
     * Get open positions for a ClientKey/AccountKey.
     * {@code GET /port/v1/positions?ClientKey=…&AccountKey=…}
     *
     * <p>Returns the {@code Data}-wrapped envelope ({@link SaxoPositionsResponse}); each row carries
     * {@code Uic}, {@code AssetType}, open {@code Amount}, {@code OpenPrice} (cost basis) and
     * {@code CurrentPrice} (SAXO_ANSWERS §2.8). The adapter reverse-maps each {@code Uic} to a
     * {@link com.quorumtrading.model.valueobject.Symbol} via the reference-data details call (SB3).</p>
     *
     * <p><b>⚠️ TODO(SB5, unconfirmed):</b> SAXO_ANSWERS §2.8 flags {@code /positions} vs
     * {@code /netpositions} as the canonical equity-holding view, and whether a {@code FieldGroups}
     * projection is needed to flatten the nested {@code PositionBase}/{@code PositionView} blocks —
     * confirm once a funded SIM account is available.</p>
     */
    @GET
    @Path("/port/v1/positions")
    SaxoPositionsResponse getPositions(@QueryParam("ClientKey") String clientKey,
                                       @QueryParam("AccountKey") String accountKey);

    /**
     * Place an order ({@code POST /trade/v2/orders}). Returns a {@code Response} (not the typed DTO)
     * so the adapter can read the {@code X-RateLimit-SessionOrders-*} headers for back-off and map a
     * non-2xx body to a rejection itself (SAXO_ANSWERS §1.6, §2.7).
     *
     * <p>The order placement service group is {@code /trade/v2}, distinct from the {@code /port/v1}
     * portfolio surface above (hence per-method absolute paths after dropping the class-level
     * {@code @Path}).</p>
     *
     * @param orderRequest the market-order body (AccountKey, Uic, AssetType, Amount, …)
     * @return the raw JAX-RS response (caller reads body + rate-limit headers)
     */
    @POST
    @Path("/trade/v2/orders")
    jakarta.ws.rs.core.Response placeOrder(SaxoOrderRequest orderRequest);

    /**
     * Get an open order's status by Saxo OrderId for an account.
     * {@code GET /port/v1/orders/{orderId}?AccountKey=…&ClientKey=…}
     *
     * <p>Order state lives in the {@code /port/v1/orders} portfolio surface (an open/working order;
     * a fully-filled or cancelled order leaves the open-orders list and 404s here).</p>
     * TODO(SB4): confirm the exact query params required for a single-order read.
     */
    @GET
    @Path("/port/v1/orders/{orderId}")
    SaxoOrderResponse getOrder(@PathParam("orderId") String orderId,
                               @QueryParam("AccountKey") String accountKey,
                               @QueryParam("ClientKey") String clientKey);

    /**
     * List the working/open orders for an account.
     * {@code GET /port/v1/orders?AccountKey=…&ClientKey=…}
     * TODO(SB4): confirm the {@code Data}-wrapped envelope + required query params.
     */
    @GET
    @Path("/port/v1/orders")
    SaxoOpenOrdersResponse getOpenOrders(@QueryParam("AccountKey") String accountKey,
                                         @QueryParam("ClientKey") String clientKey);

    /**
     * Cancel an order by Saxo OrderId.
     * {@code DELETE /trade/v2/orders/{orderId}?AccountKey=…}
     * (Saxo accepts a comma-separated id list at this path; one id per call here.)
     * TODO(SB4): confirm AccountKey is the required query param for a cancel.
     */
    @DELETE
    @Path("/trade/v2/orders/{orderId}")
    void cancelOrder(@PathParam("orderId") String orderId,
                     @QueryParam("AccountKey") String accountKey);
}
