package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response from the Saxo OpenAPI accounts endpoint
 * ({@code GET /port/v1/accounts/me}), which returns the caller's account list
 * under a {@code Data} array.
 *
 * <p><b>Scaffold (SB1).</b> The adapter needs the {@code AccountKey} (the handle
 * every trade/balance call is scoped to) and the account's settlement
 * {@code Currency} — the latter feeds the EUR settlement-currency thread
 * ({@code CUR1}). Resolution + per-account caching is wired in
 * {@code SB2-auth-accountkey}.</p>
 *
 * <p><b>TODO(SB2):</b> confirm the {@code /port/v1/accounts/me} envelope — Saxo
 * wraps the list in {@code Data}; verify {@code AccountKey}, {@code AccountId},
 * {@code Currency}, {@code Active}, and whether a client can have several accounts
 * (pick the EUR one / the configured one).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoAccountResponse {

    /** The account-list envelope. TODO(SB2): confirm Saxo wraps accounts in {@code Data}. */
    @JsonProperty("Data")
    private List<SaxoAccount> data;

    public List<SaxoAccount> getData() {
        return data;
    }

    public void setData(List<SaxoAccount> data) {
        this.data = data;
    }

    /**
     * A single Saxo account row. <b>Scaffold (SB1)</b> — TODO(SB2): confirm field
     * names ({@code AccountKey}, {@code AccountId}, {@code Currency}, {@code Active}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaxoAccount {

        /** Opaque account handle used to scope trade/balance calls. TODO(SB2): confirm {@code AccountKey}. */
        @JsonProperty("AccountKey")
        private String accountKey;

        /** Human-facing account number. TODO(SB2): confirm {@code AccountId}. */
        @JsonProperty("AccountId")
        private String accountId;

        /** ISO-4217 settlement currency, expected {@code EUR}. TODO(SB2/CUR1): confirm {@code Currency}. */
        @JsonProperty("Currency")
        private String currency;

        /** Whether the account is active/tradable. TODO(SB2): confirm {@code Active}. */
        @JsonProperty("Active")
        private Boolean active;

        public String getAccountKey() {
            return accountKey;
        }

        public void setAccountKey(String accountKey) {
            this.accountKey = accountKey;
        }

        public String getAccountId() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId = accountId;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }
    }
}
