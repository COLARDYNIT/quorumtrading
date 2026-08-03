package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Account-balance response from the Saxo OpenAPI balance endpoint
 * ({@code GET /port/v1/balances/me} or {@code /port/v1/balances?AccountKey=…}).
 *
 * <p><b>Scaffold (SB1).</b> Surfaces cash + settlement {@code Currency} so the
 * adapter can build an EUR {@link com.quorumtrading.model.valueobject.Money}
 * available-balance (Saxo Belgium settles in EUR — see plan §6 D7 / CUR1). The
 * cash/equity → {@code Money} translation is wired in
 * {@code SB5-portfolio-balance-validate}.</p>
 *
 * <p><b>TODO(SB5):</b> confirm the {@code /port/v1} field names — Saxo exposes
 * {@code CashBalance}, {@code TotalValue}, {@code MarginAvailableForTrading} etc.;
 * pick the one that matches our "available cash, no margin" semantics and confirm
 * {@code Currency} is on the balance payload (vs the account payload).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoBalanceResponse {

    /** Settled cash balance. TODO(SB5): confirm {@code CashBalance} vs {@code TotalCash}. */
    @JsonProperty("CashBalance")
    private BigDecimal cashBalance;

    /** Total account value (cash + positions). TODO(SB5): confirm {@code TotalValue}. */
    @JsonProperty("TotalValue")
    private BigDecimal totalValue;

    /** Cash available to trade with (no margin). TODO(SB5): confirm exact field. */
    @JsonProperty("CashAvailableForTrading")
    private BigDecimal cashAvailableForTrading;

    /** ISO-4217 settlement currency, expected {@code EUR}. TODO(SB5): confirm field placement. */
    @JsonProperty("Currency")
    private String currency;

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getCashAvailableForTrading() {
        return cashAvailableForTrading;
    }

    public void setCashAvailableForTrading(BigDecimal cashAvailableForTrading) {
        this.cashAvailableForTrading = cashAvailableForTrading;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
