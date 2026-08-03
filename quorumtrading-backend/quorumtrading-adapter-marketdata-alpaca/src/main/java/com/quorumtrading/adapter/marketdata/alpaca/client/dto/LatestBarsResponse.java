package com.quorumtrading.adapter.marketdata.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Latest bars response from Alpaca Market Data API.
 * Used for both stock and crypto latest bars endpoints.
 *
 * Mapped from OpenAPI schemas: stock_latest_bars_resp, crypto_latest_bars_resp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LatestBarsResponse {

    @JsonProperty("bars")
    private Map<String, BarData> bars;

    // Getters
    public Map<String, BarData> getBars() {
        return bars;
    }

    // Setters (for Jackson deserialization)
    public void setBars(Map<String, BarData> bars) {
        this.bars = bars;
    }
}
