package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * {@code Data}-wrapped envelope for the Saxo open/working orders list
 * ({@code GET /port/v1/orders?AccountKey=…&ClientKey=…}).
 *
 * <p><b>SB4-trade-execution.</b> Used by {@code cancelAllOpenOrders} to enumerate the working
 * orders for an account and cancel each by its {@code OrderId}. Saxo wraps portfolio collections
 * in a {@code Data} array (SAXO_ANSWERS §2.8 pattern); each row carries at least the {@code OrderId}.</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps the DTO tolerant of the many
 * order fields not consumed here.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoOpenOrdersResponse {

    @JsonProperty("Data")
    private List<SaxoOpenOrder> data;

    public List<SaxoOpenOrder> getData() {
        return data;
    }

    public void setData(List<SaxoOpenOrder> data) {
        this.data = data;
    }

    /** A single working/open order row (the {@code OrderId} is all the cancel path needs). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaxoOpenOrder {

        @JsonProperty("OrderId")
        private String orderId;

        @JsonProperty("Status")
        private String status;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
