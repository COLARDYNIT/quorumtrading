package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single instrument hit from the Saxo Reference Data instrument-search endpoint
 * ({@code GET /port/v1/ref/v1/instruments?Keywords=…}).
 *
 * <p><b>Scaffold (SB1).</b> This is the lightweight "summary" projection Saxo
 * returns from a keyword search — enough to disambiguate a multi-listing name down
 * to one {@code Uic}+{@code AssetType} (by primary listing → MIC → currency). The
 * disambiguation logic and {@code Symbol}→{@code Uic} resolution land in
 * {@code SB3-uic-resolver}; this DTO only models the search-result shape.</p>
 *
 * <p><b>TODO(SB3):</b> confirm the {@code ref/v1/instruments} field names — Saxo
 * returns {@code Identifier} (the Uic), {@code Symbol}, {@code Description},
 * {@code AssetType}, {@code ExchangeId}, and {@code CurrencyCode}; verify casing
 * and whether the search returns a MIC directly or an internal {@code ExchangeId}
 * needing a second {@code /ref/v1/exchanges} lookup.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoInstrumentSummary {

    /** Numeric Saxo instrument id. TODO(SB3): Saxo names this {@code Identifier} in search results — confirm. */
    @JsonProperty("Identifier")
    private Long uic;

    /** Saxo symbol/ticker root, e.g. {@code RHM:xetr}. TODO(SB3): confirm. */
    @JsonProperty("Symbol")
    private String symbol;

    /** Human-readable instrument name. TODO(SB3): confirm {@code Description}. */
    @JsonProperty("Description")
    private String description;

    /** Saxo asset type, e.g. {@code Stock}, {@code Etf}. TODO(SB3): confirm enum casing. */
    @JsonProperty("AssetType")
    private String assetType;

    /** Saxo internal exchange id (NOT a MIC). TODO(SB3): may need {@code /ref/v1/exchanges} to map → MIC. */
    @JsonProperty("ExchangeId")
    private String exchangeId;

    /** ISO-4217 trading currency. TODO(SB3): confirm {@code CurrencyCode}. */
    @JsonProperty("CurrencyCode")
    private String currencyCode;

    public Long getUic() {
        return uic;
    }

    public void setUic(Long uic) {
        this.uic = uic;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }
}
