package com.quorumtrading.adapter.broker.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Order request for Alpaca Trading API.
 * Mapped from OpenAPI path: POST /v2/orders
 */
public class OrderRequest {

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("qty")
    private String qty;

    @JsonProperty("side")
    private String side; // "buy" or "sell"

    @JsonProperty("type")
    private String type; // "market", "limit", "stop", "stop_limit", "trailing_stop"

    @JsonProperty("time_in_force")
    private String timeInForce; // "day", "gtc", "ioc", "fok"

    @JsonProperty("limit_price")
    private String limitPrice;

    @JsonProperty("stop_price")
    private String stopPrice;

    @JsonProperty("client_order_id")
    private String clientOrderId;

    @JsonProperty("extended_hours")
    private Boolean extendedHours;

    // Constructors
    public OrderRequest() {
    }

    public static OrderRequest marketOrder(String symbol, String qty, String side, String timeInForce, boolean enableExtendedHours) {
        OrderRequest request = new OrderRequest();
        request.symbol = symbol;
        request.qty = qty;
        request.side = side;
        request.type = "market";
        request.timeInForce = timeInForce; // "day" for stocks (with extended hours), "ioc" for crypto (24/7)
        request.extendedHours = enableExtendedHours;
        return request;
    }

    // Getters and setters
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getQty() {
        return qty;
    }

    public void setQty(String qty) {
        this.qty = qty;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(String timeInForce) {
        this.timeInForce = timeInForce;
    }

    public String getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(String limitPrice) {
        this.limitPrice = limitPrice;
    }

    public String getStopPrice() {
        return stopPrice;
    }

    public void setStopPrice(String stopPrice) {
        this.stopPrice = stopPrice;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public void setClientOrderId(String clientOrderId) {
        this.clientOrderId = clientOrderId;
    }

    public Boolean getExtendedHours() {
        return extendedHours;
    }

    public void setExtendedHours(Boolean extendedHours) {
        this.extendedHours = extendedHours;
    }
}
