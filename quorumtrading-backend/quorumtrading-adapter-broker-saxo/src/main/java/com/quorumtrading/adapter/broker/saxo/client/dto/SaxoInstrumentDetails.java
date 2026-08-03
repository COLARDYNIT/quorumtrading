package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The full instrument-details projection from the Saxo Reference Data
 * instrument-details endpoint
 * ({@code GET /port/v1/ref/v1/instruments/details/{uic}/{assetType}}).
 *
 * <p><b>Scaffold (SB1).</b> Richer than {@link SaxoInstrumentSummary}: carries the
 * tradability flag, lot size, and the primary-listing marker the
 * {@code SB3-uic-resolver} needs to pick the right venue, and feeds the
 * account-aware {@code isSymbolTradable}/{@code getAssetInfo} overrides
 * ({@code SB5}). Modelled here as a shape only.</p>
 *
 * <p><b>SB3-uic-resolver.</b> Carries the tradability flag, the primary-listing marker,
 * and the per-listing trading constraints the resolver surfaces to {@code AssetInfo} and the
 * whole-share sizing chain ({@code SB4}/{@code FX}): {@code LotSize}, {@code MinimumTradeSize},
 * {@code MinimumOrderValue}, {@code AmountDecimals} (0 ⇒ non-fractional equity/ETF), and
 * {@code TickSize}. {@code ExchangeId} echoes the venue so a multi-listing pick can be
 * re-confirmed.</p>
 *
 * <p><b>⚠️ TODO(SB3/SB5, unconfirmed):</b> field names/casing/nesting are best-effort against
 * SAXO_ANSWERS §2.2 — {@code Exchange}/{@code ExchangeSummary} may nest the MIC; verify once the
 * vendor confirms plan §9 Q2.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoInstrumentDetails {

    /** Numeric Saxo instrument id. TODO(SB3): confirm {@code Uic}. */
    @JsonProperty("Uic")
    private Long uic;

    /** Saxo asset type, e.g. {@code Stock}, {@code Etf}. TODO(SB3): confirm enum casing. */
    @JsonProperty("AssetType")
    private String assetType;

    /** Saxo symbol/ticker root, e.g. {@code RHM:xetr}. TODO(SB3): confirm {@code Symbol}. */
    @JsonProperty("Symbol")
    private String symbol;

    /** Human-readable instrument name. TODO(SB3): confirm {@code Description}. */
    @JsonProperty("Description")
    private String description;

    /** ISO-4217 trading currency. TODO(SB3): confirm {@code CurrencyCode}. */
    @JsonProperty("CurrencyCode")
    private String currencyCode;

    /** Saxo internal exchange id (NOT necessarily an ISO MIC). TODO(SB3): confirm {@code ExchangeId}. */
    @JsonProperty("ExchangeId")
    private String exchangeId;

    /** Whether the instrument is currently tradable on this venue. TODO(SB5): confirm {@code IsTradable}. */
    @JsonProperty("IsTradable")
    private Boolean isTradable;

    /** Uic of the primary listing (for multi-listing disambiguation). TODO(SB3): confirm {@code PrimaryListing}. */
    @JsonProperty("PrimaryListing")
    private Long primaryListing;

    /**
     * Minimum tradable order size in shares. TODO(SB3): confirm {@code MinimumTradeSize}.
     */
    @JsonProperty("MinimumTradeSize")
    private java.math.BigDecimal minimumTradeSize;

    /**
     * Board-lot increment in shares (orders must be whole multiples of this). Often 1 for
     * Xetra/Euronext equities but not always. TODO(SB3): confirm {@code LotSize}.
     */
    @JsonProperty("LotSize")
    private java.math.BigDecimal lotSize;

    /**
     * Minimum order value (Amount × Price must be ≥ this), in {@link #currencyCode}.
     * TODO(SB3): confirm {@code MinimumOrderValue}.
     */
    @JsonProperty("MinimumOrderValue")
    private java.math.BigDecimal minimumOrderValue;

    /**
     * Number of decimal places allowed on the order {@code Amount}: {@code 0} confirms a
     * whole-share-only (non-fractional) instrument, which is the case for Saxo cash
     * equities/ETFs (SAXO_ANSWERS §2.1/§2.2). TODO(SB3): confirm {@code AmountDecimals}.
     */
    @JsonProperty("AmountDecimals")
    private Integer amountDecimals;

    /** Minimum price increment for limit orders. TODO(SB3): confirm {@code TickSize}. */
    @JsonProperty("TickSize")
    private java.math.BigDecimal tickSize;

    public Long getUic() {
        return uic;
    }

    public void setUic(Long uic) {
        this.uic = uic;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
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

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(String exchangeId) {
        this.exchangeId = exchangeId;
    }

    public Boolean getIsTradable() {
        return isTradable;
    }

    public void setIsTradable(Boolean isTradable) {
        this.isTradable = isTradable;
    }

    public Long getPrimaryListing() {
        return primaryListing;
    }

    public void setPrimaryListing(Long primaryListing) {
        this.primaryListing = primaryListing;
    }

    public java.math.BigDecimal getMinimumTradeSize() {
        return minimumTradeSize;
    }

    public void setMinimumTradeSize(java.math.BigDecimal minimumTradeSize) {
        this.minimumTradeSize = minimumTradeSize;
    }

    public java.math.BigDecimal getLotSize() {
        return lotSize;
    }

    public void setLotSize(java.math.BigDecimal lotSize) {
        this.lotSize = lotSize;
    }

    public java.math.BigDecimal getMinimumOrderValue() {
        return minimumOrderValue;
    }

    public void setMinimumOrderValue(java.math.BigDecimal minimumOrderValue) {
        this.minimumOrderValue = minimumOrderValue;
    }

    public Integer getAmountDecimals() {
        return amountDecimals;
    }

    public void setAmountDecimals(Integer amountDecimals) {
        this.amountDecimals = amountDecimals;
    }

    public java.math.BigDecimal getTickSize() {
        return tickSize;
    }

    public void setTickSize(java.math.BigDecimal tickSize) {
        this.tickSize = tickSize;
    }
}
