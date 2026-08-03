package com.quorumtrading.adapter.broker.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Account response from Alpaca Trading API.
 * Mapped from OpenAPI schema: #/components/schemas/Account
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("cash")
    private String cash;

    @JsonProperty("buying_power")
    private String buyingPower;

    @JsonProperty("equity")
    private String equity;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("account_blocked")
    private Boolean accountBlocked;

    // Getters
    public String getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCash() {
        return cash;
    }

    public BigDecimal getCashAsDecimal() {
        return cash != null ? new BigDecimal(cash) : BigDecimal.ZERO;
    }

    public String getBuyingPower() {
        return buyingPower;
    }

    public String getEquity() {
        return equity;
    }

    public String getCurrency() {
        return currency;
    }

    public Boolean getAccountBlocked() {
        return accountBlocked;
    }

    // Setters (for Jackson deserialization)
    public void setId(String id) {
        this.id = id;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public void setCash(String cash) {
        this.cash = cash;
    }

    public void setBuyingPower(String buyingPower) {
        this.buyingPower = buyingPower;
    }

    public void setEquity(String equity) {
        this.equity = equity;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setAccountBlocked(Boolean accountBlocked) {
        this.accountBlocked = accountBlocked;
    }
}
