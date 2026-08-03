package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * A single open-position row from the Saxo OpenAPI positions endpoint
 * ({@code GET /port/v1/positions/me} or {@code /port/v1/positions?…}).
 *
 * <p><b>Scaffold (SB1).</b> Saxo nests the trade-identifying fields under a
 * {@code PositionBase} block and the live valuation under a {@code PositionView}
 * block; this scaffold flattens only the handful the adapter needs (Uic,
 * AssetType, open amount, open/cost price, current value) so
 * {@code SB5-portfolio-balance-validate} can reconstruct
 * {@link com.quorumtrading.model.entity.Position}s keyed by the resolved
 * {@link com.quorumtrading.model.valueobject.Symbol}.</p>
 *
 * <p><b>TODO(SB5):</b> the Saxo positions payload is nested
 * ({@code PositionBase}/{@code PositionView}); confirm whether to model the nested
 * objects or request a flattened {@code FieldGroups} projection, and confirm each
 * property name. Reverse Uic→Symbol resolution is owned by {@code SB3-uic-resolver}
 * (reuse the model {@code SymbolParser} for MIC/currency, do not re-roll tables).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoPositionResponse {

    /** Numeric Saxo instrument id of the held instrument. TODO(SB5): confirm nesting/name. */
    @JsonProperty("Uic")
    private Long uic;

    /** Saxo asset type of the held instrument. TODO(SB5): confirm nesting/name. */
    @JsonProperty("AssetType")
    private String assetType;

    /** Signed open share count (positive long / negative short). TODO(SB5): confirm {@code Amount}. */
    @JsonProperty("Amount")
    private BigDecimal amount;

    /** Average open price (cost basis per share). TODO(SB5): confirm {@code OpenPrice}. */
    @JsonProperty("OpenPrice")
    private BigDecimal openPrice;

    /** Current market price per share (for live valuation). TODO(SB5): confirm {@code CurrentPrice}. */
    @JsonProperty("CurrentPrice")
    private BigDecimal currentPrice;

    /** Current market value of the position. TODO(SB5): confirm {@code MarketValue}/{@code CurrentPrice}. */
    @JsonProperty("MarketValue")
    private BigDecimal marketValue;

    /** Unrealised P/L on the open lot, in the account currency. TODO(SB5): confirm {@code ProfitLossOnTrade}. */
    @JsonProperty("ProfitLossOnTrade")
    private BigDecimal profitLossOnTrade;

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getOpenPrice() {
        return openPrice;
    }

    public void setOpenPrice(BigDecimal openPrice) {
        this.openPrice = openPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    public BigDecimal getProfitLossOnTrade() {
        return profitLossOnTrade;
    }

    public void setProfitLossOnTrade(BigDecimal profitLossOnTrade) {
        this.profitLossOnTrade = profitLossOnTrade;
    }
}
