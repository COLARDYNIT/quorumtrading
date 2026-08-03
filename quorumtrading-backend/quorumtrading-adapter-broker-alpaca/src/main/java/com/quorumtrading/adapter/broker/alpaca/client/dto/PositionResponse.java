package com.quorumtrading.adapter.broker.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Position response from Alpaca Trading API.
 * Mapped from OpenAPI schema: #/components/schemas/Position
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionResponse {

    @JsonProperty("asset_id")
    private String assetId;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("asset_class")
    private String assetClass;

    @JsonProperty("qty")
    private String qty;

    @JsonProperty("avg_entry_price")
    private String avgEntryPrice;

    @JsonProperty("side")
    private String side;

    @JsonProperty("market_value")
    private String marketValue;

    @JsonProperty("cost_basis")
    private String costBasis;

    @JsonProperty("current_price")
    private String currentPrice;

    // Getters
    public String getAssetId() {
        return assetId;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getExchange() {
        return exchange;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public String getQty() {
        return qty;
    }

    public BigDecimal getQtyAsDecimal() {
        return qty != null ? new BigDecimal(qty) : BigDecimal.ZERO;
    }

    public String getAvgEntryPrice() {
        return avgEntryPrice;
    }

    public BigDecimal getAvgEntryPriceAsDecimal() {
        return avgEntryPrice != null ? new BigDecimal(avgEntryPrice) : BigDecimal.ZERO;
    }

    public String getSide() {
        return side;
    }

    public String getMarketValue() {
        return marketValue;
    }

    public String getCostBasis() {
        return costBasis;
    }

    public String getCurrentPrice() {
        return currentPrice;
    }

    // Setters (for Jackson deserialization)
    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public void setQty(String qty) {
        this.qty = qty;
    }

    public void setAvgEntryPrice(String avgEntryPrice) {
        this.avgEntryPrice = avgEntryPrice;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public void setMarketValue(String marketValue) {
        this.marketValue = marketValue;
    }

    public void setCostBasis(String costBasis) {
        this.costBasis = costBasis;
    }

    public void setCurrentPrice(String currentPrice) {
        this.currentPrice = currentPrice;
    }
}
