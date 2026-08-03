package com.quorumtrading.adapter.marketdata.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Bar (candlestick) data from Alpaca Market Data API.
 * Used for both stock and crypto bar responses.
 *
 * Mapped from OpenAPI schema components (stock_bar, crypto_bar).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BarData {

    @JsonProperty("t")
    private String timestamp;

    @JsonProperty("o")
    private BigDecimal open;

    @JsonProperty("h")
    private BigDecimal high;

    @JsonProperty("l")
    private BigDecimal low;

    @JsonProperty("c")
    private BigDecimal close;

    @JsonProperty("v")
    private BigDecimal volume;

    @JsonProperty("n")
    private Long tradeCount;

    @JsonProperty("vw")
    private BigDecimal vwap; // Volume Weighted Average Price

    // Getters
    public String getTimestamp() {
        return timestamp;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public Long getTradeCount() {
        return tradeCount;
    }

    public BigDecimal getVwap() {
        return vwap;
    }

    // Setters (for Jackson deserialization)
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
    }

    public void setTradeCount(Long tradeCount) {
        this.tradeCount = tradeCount;
    }

    public void setVwap(BigDecimal vwap) {
        this.vwap = vwap;
    }
}
