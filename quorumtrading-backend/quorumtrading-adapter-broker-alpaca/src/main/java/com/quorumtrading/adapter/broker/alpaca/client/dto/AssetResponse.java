package com.quorumtrading.adapter.broker.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Asset response from Alpaca Trading API.
 * Mapped from GET /v2/assets/{symbol_or_asset_id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssetResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("name")
    private String name;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("asset_class")
    private String assetClass;

    @JsonProperty("status")
    private String status;

    @JsonProperty("tradable")
    private boolean tradable;

    @JsonProperty("fractionable")
    private boolean fractionable;

    // Getters

    public String getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getExchange() {
        return exchange;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public String getStatus() {
        return status;
    }

    public boolean isTradable() {
        return tradable;
    }

    public boolean isFractionable() {
        return fractionable;
    }

    // Setters (for Jackson deserialization)

    public void setId(String id) {
        this.id = id;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public void setAssetClass(String assetClass) {
        this.assetClass = assetClass;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setTradable(boolean tradable) {
        this.tradable = tradable;
    }

    public void setFractionable(boolean fractionable) {
        this.fractionable = fractionable;
    }
}
