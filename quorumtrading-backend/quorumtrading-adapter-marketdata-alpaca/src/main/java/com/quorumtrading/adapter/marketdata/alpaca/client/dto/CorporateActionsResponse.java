package com.quorumtrading.adapter.marketdata.alpaca.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response from Alpaca Corporate Actions API.
 * GET /v1/corporate-actions?symbols=AAPL,MSFT&types=cash_dividend
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CorporateActionsResponse {

    @JsonProperty("corporate_actions")
    private CorporateActions corporateActions;

    @JsonProperty("next_page_token")
    private String nextPageToken;

    public CorporateActions getCorporateActions() {
        return corporateActions;
    }

    public void setCorporateActions(CorporateActions corporateActions) {
        this.corporateActions = corporateActions;
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CorporateActions {

        @JsonProperty("cash_dividends")
        private List<CashDividend> cashDividends;

        public List<CashDividend> getCashDividends() {
            return cashDividends;
        }

        public void setCashDividends(List<CashDividend> cashDividends) {
            this.cashDividends = cashDividends;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CashDividend {

        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("rate")
        private BigDecimal rate;

        @JsonProperty("ex_date")
        private String exDate;

        @JsonProperty("record_date")
        private String recordDate;

        @JsonProperty("payable_date")
        private String payableDate;

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public BigDecimal getRate() {
            return rate;
        }

        public void setRate(BigDecimal rate) {
            this.rate = rate;
        }

        public String getExDate() {
            return exDate;
        }

        public void setExDate(String exDate) {
            this.exDate = exDate;
        }

        public String getRecordDate() {
            return recordDate;
        }

        public void setRecordDate(String recordDate) {
            this.recordDate = recordDate;
        }

        public String getPayableDate() {
            return payableDate;
        }

        public void setPayableDate(String payableDate) {
            this.payableDate = payableDate;
        }
    }
}
