package com.quorumtrading.adapter.marketdata.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Historical bars response from Alpaca Market Data API.
 * Used for both stock and crypto historical bars endpoints.
 *
 * Mapped from OpenAPI schemas: stock_bars_resp, crypto_bars_resp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoricalBarsResponse {

    /**
     * Map of symbol → list of bars.
     * Each symbol can have multiple bars in the requested time range.
     */
    @JsonProperty("bars")
    private Map<String, List<BarData>> bars;

    /**
     * Pagination token for fetching next page of results.
     * Null if no more pages available.
     */
    @JsonProperty("next_page_token")
    private String nextPageToken;

    /**
     * Currency for stock pricing (stocks only).
     * Optional field.
     */
    @JsonProperty("currency")
    private String currency;

    // Getters
    public Map<String, List<BarData>> getBars() {
        return bars;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public String getCurrency() {
        return currency;
    }

    // Setters (for Jackson deserialization)
    public void setBars(Map<String, List<BarData>> bars) {
        this.bars = bars;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
