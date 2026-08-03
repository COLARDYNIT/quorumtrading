package com.quorumtrading.adapter.marketdata.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single instrument hit from the Saxo Reference Data instrument-search endpoint
 * ({@code GET /ref/v1/instruments?Keywords=…&AssetTypes=…&ExchangeId=…}).
 *
 * <p><b>MD2-live-quotes — module-local copy.</b> Adapters must not depend on each other
 * (hexagonal layering, ArchUnit-enforced), so the Saxo market-data module owns its <em>own</em>
 * reference-data search DTO rather than importing the broker-saxo {@code SaxoInstrumentSummary}.
 * The market-data resolver only needs the {@code (Uic, AssetType)} identity (plus the symbol
 * root + currency to disambiguate / stamp the event), so this is the lighter summary projection.</p>
 *
 * <p><b>⚠️ TODO(MD2, confirm against LIVE):</b> Saxo names the numeric Uic {@code Identifier} in
 * search results; verify casing and whether the search returns a MIC directly or an internal
 * {@code ExchangeId} (SAXO_ANSWERS §2.3).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoMdInstrumentSummary {

    /** Numeric Saxo instrument id (Saxo names it {@code Identifier} in search results). */
    @JsonProperty("Identifier")
    private Long uic;

    /** Saxo symbol/ticker root, e.g. {@code RHM:xetr}. */
    @JsonProperty("Symbol")
    private String symbol;

    /** Human-readable instrument name. */
    @JsonProperty("Description")
    private String description;

    /** Saxo asset type, e.g. {@code Stock}, {@code Etf}. */
    @JsonProperty("AssetType")
    private String assetType;

    /** Saxo internal exchange id (NOT necessarily an ISO MIC). */
    @JsonProperty("ExchangeId")
    private String exchangeId;

    /** ISO-4217 trading currency. */
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
