package com.quorumtrading.adapter.broker.alpaca.client;

import com.quorumtrading.adapter.broker.alpaca.client.dto.AccountResponse;
import com.quorumtrading.adapter.broker.alpaca.client.dto.AssetResponse;
import com.quorumtrading.adapter.broker.alpaca.client.dto.OrderRequest;
import com.quorumtrading.adapter.broker.alpaca.client.dto.OrderResponse;
import com.quorumtrading.adapter.broker.alpaca.client.dto.PositionResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * Alpaca Trading API REST client.
 *
 * Based on Alpaca Trading API v2 OpenAPI specification.
 * Base URL: https://paper-api.alpaca.markets (paper trading) or https://api.alpaca.markets (live trading)
 *
 * Authentication: Requires APCA-API-KEY-ID and APCA-API-SECRET-KEY headers (configured via ClientHeadersFactory)
 */
@RegisterRestClient(configKey = "alpaca-trading")
@RegisterClientHeaders(AlpacaAuthHeadersFactory.class)
@Path("/v2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AlpacaTradingClient {

    /**
     * Get account information.
     * GET /v2/account
     */
    @GET
    @Path("/account")
    AccountResponse getAccount();

    /**
     * Get all open positions.
     * GET /v2/positions
     */
    @GET
    @Path("/positions")
    List<PositionResponse> getPositions();

    /**
     * Get a specific position by symbol or asset ID.
     * GET /v2/positions/{symbol_or_asset_id}
     */
    @GET
    @Path("/positions/{symbol_or_asset_id}")
    PositionResponse getPosition(@PathParam("symbol_or_asset_id") String symbolOrAssetId);

    /**
     * Submit a new order.
     * POST /v2/orders
     */
    @POST
    @Path("/orders")
    OrderResponse submitOrder(OrderRequest orderRequest);

    /**
     * Get an order by ID.
     * GET /v2/orders/{order_id}
     */
    @GET
    @Path("/orders/{order_id}")
    OrderResponse getOrder(@PathParam("order_id") String orderId);

    /**
     * Get all orders.
     * GET /v2/orders
     */
    @GET
    @Path("/orders")
    List<OrderResponse> getOrders(@QueryParam("status") String status,
                                   @QueryParam("limit") Integer limit,
                                   @QueryParam("after") String after,
                                   @QueryParam("until") String until,
                                   @QueryParam("direction") String direction,
                                   @QueryParam("nested") Boolean nested);

    /**
     * Cancel an order by ID.
     * DELETE /v2/orders/{order_id}
     */
    @DELETE
    @Path("/orders/{order_id}")
    void cancelOrder(@PathParam("order_id") String orderId);

    /**
     * Get asset information by symbol or asset ID.
     * GET /v2/assets/{symbol_or_asset_id}
     *
     * Returns 404 if the asset does not exist.
     */
    @GET
    @Path("/assets/{symbol_or_asset_id}")
    AssetResponse getAsset(@PathParam("symbol_or_asset_id") String symbolOrAssetId);
}
