package com.quorumtrading.adapter.marketdata.tiingo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/**
 * DTO for Tiingo daily price bar response.
 *
 * Tiingo returns split+dividend adjusted prices by default via the adj* fields.
 * We use adjOpen/adjHigh/adjLow/adjClose for consistency with our split-adjusted Alpaca data.
 *
 * Example JSON from Tiingo:
 * {
 *   "adjClose": 145.23,
 *   "adjHigh": 146.50,
 *   "adjLow": 144.10,
 *   "adjOpen": 145.00,
 *   "adjVolume": 12345678,
 *   "close": 145.23,
 *   "date": "2025-01-15T00:00:00+00:00",
 *   "divCash": 0.0,
 *   "high": 146.50,
 *   "low": 144.10,
 *   "open": 145.00,
 *   "splitFactor": 1.0,
 *   "volume": 12345678
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TiingoPriceBar {

    private String date;
    private BigDecimal adjOpen;
    private BigDecimal adjHigh;
    private BigDecimal adjLow;
    private BigDecimal adjClose;
    private Long adjVolume;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private Long volume;
    private BigDecimal divCash;
    private BigDecimal splitFactor;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getAdjOpen() {
        return adjOpen;
    }

    public void setAdjOpen(BigDecimal adjOpen) {
        this.adjOpen = adjOpen;
    }

    public BigDecimal getAdjHigh() {
        return adjHigh;
    }

    public void setAdjHigh(BigDecimal adjHigh) {
        this.adjHigh = adjHigh;
    }

    public BigDecimal getAdjLow() {
        return adjLow;
    }

    public void setAdjLow(BigDecimal adjLow) {
        this.adjLow = adjLow;
    }

    public BigDecimal getAdjClose() {
        return adjClose;
    }

    public void setAdjClose(BigDecimal adjClose) {
        this.adjClose = adjClose;
    }

    public Long getAdjVolume() {
        return adjVolume;
    }

    public void setAdjVolume(Long adjVolume) {
        this.adjVolume = adjVolume;
    }

    public BigDecimal getOpen() {
        return open;
    }

    public void setOpen(BigDecimal open) {
        this.open = open;
    }

    public BigDecimal getHigh() {
        return high;
    }

    public void setHigh(BigDecimal high) {
        this.high = high;
    }

    public BigDecimal getLow() {
        return low;
    }

    public void setLow(BigDecimal low) {
        this.low = low;
    }

    public BigDecimal getClose() {
        return close;
    }

    public void setClose(BigDecimal close) {
        this.close = close;
    }

    public Long getVolume() {
        return volume;
    }

    public void setVolume(Long volume) {
        this.volume = volume;
    }

    public BigDecimal getDivCash() {
        return divCash;
    }

    public void setDivCash(BigDecimal divCash) {
        this.divCash = divCash;
    }

    public BigDecimal getSplitFactor() {
        return splitFactor;
    }

    public void setSplitFactor(BigDecimal splitFactor) {
        this.splitFactor = splitFactor;
    }
}
