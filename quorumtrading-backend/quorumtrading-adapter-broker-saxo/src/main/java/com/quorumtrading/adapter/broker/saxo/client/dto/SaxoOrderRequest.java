package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Order placement request for the Saxo OpenAPI trade endpoint
 * ({@code POST /trade/v2/orders}).
 *
 * <p><b>SB4-trade-execution.</b> Field set is the confirmed Saxo market-order body for a cash
 * equity/ETF (SAXO_ANSWERS §2.7): numeric {@code Uic} + {@code AssetType} instrument identity
 * (NOT a ticker string), a whole-share {@code Amount}, {@code BuySell} side, {@code OrderType=Market},
 * a structured {@code OrderDuration} object ({@code {DurationType: DayOrder}}), {@code ManualOrder:false}
 * for an automated app, the required {@code AccountKey}, and our {@code ExternalReference} correlation
 * id (echoed back; a distinct value per call is what defeats Saxo's 15-second duplicate-order
 * protection — §1.6).</p>
 *
 * <p>{@code AmountType} is left unset so Saxo defaults it to {@code Quantity} (a share count);
 * {@code CashAmount} notional ordering is MutualFunds-only and is deliberately never sent for
 * equities/ETFs (§2.1).</p>
 *
 * <p><b>⚠️ TODO(SB4, unconfirmed):</b> the exact {@code /trade/v2} property names/casing are
 * best-effort against the Saxo OpenAPI order-placement docs — verify against the live spec /
 * SIM round-trip and add limit/stop variants if needed. Saxo enforces whole-share {@code Amount}
 * on equities/ETFs (no fractional path — plan §6 D3).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SaxoOrderRequest {

    /** Required Saxo AccountKey the order is placed against. */
    @JsonProperty("AccountKey")
    private String accountKey;

    /** Numeric Saxo instrument id. */
    @JsonProperty("Uic")
    private Long uic;

    /** Saxo asset type, e.g. {@code Stock}, {@code Etf}. */
    @JsonProperty("AssetType")
    private String assetType;

    /** {@code Buy} or {@code Sell}. */
    @JsonProperty("BuySell")
    private String buySell;

    /** Whole-share count to trade (Saxo equities/ETFs are non-fractional). */
    @JsonProperty("Amount")
    private BigDecimal amount;

    /** Order type, e.g. {@code Market}. */
    @JsonProperty("OrderType")
    private String orderType;

    /** Whether the order was placed manually (Saxo compliance flag; {@code false} for an automated app). */
    @JsonProperty("ManualOrder")
    private Boolean manualOrder;

    /** Structured time-in-force, e.g. {@code {DurationType: DayOrder}}. */
    @JsonProperty("OrderDuration")
    private SaxoOrderDuration orderDuration;

    /** Our correlation id echoed back by Saxo; a distinct value per call defeats the 15s duplicate window. */
    @JsonProperty("ExternalReference")
    private String externalReference;

    public SaxoOrderRequest() {
    }

    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

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

    public String getBuySell() {
        return buySell;
    }

    public void setBuySell(String buySell) {
        this.buySell = buySell;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public Boolean getManualOrder() {
        return manualOrder;
    }

    public void setManualOrder(Boolean manualOrder) {
        this.manualOrder = manualOrder;
    }

    public SaxoOrderDuration getOrderDuration() {
        return orderDuration;
    }

    public void setOrderDuration(SaxoOrderDuration orderDuration) {
        this.orderDuration = orderDuration;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    /**
     * Structured {@code OrderDuration} object. For a day market order this is
     * {@code {"DurationType": "DayOrder"}} (SAXO_ANSWERS §2.7).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SaxoOrderDuration {

        @JsonProperty("DurationType")
        private String durationType;

        public SaxoOrderDuration() {
        }

        public SaxoOrderDuration(String durationType) {
            this.durationType = durationType;
        }

        public String getDurationType() {
            return durationType;
        }

        public void setDurationType(String durationType) {
            this.durationType = durationType;
        }
    }
}
