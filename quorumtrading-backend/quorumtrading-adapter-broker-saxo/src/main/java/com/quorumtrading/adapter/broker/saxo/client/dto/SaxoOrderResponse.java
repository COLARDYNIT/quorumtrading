package com.quorumtrading.adapter.broker.saxo.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response from the Saxo OpenAPI order-placement endpoint ({@code POST /trade/v2/orders}).
 *
 * <p><b>SB4-trade-execution.</b> On a successful placement Saxo returns the broker-side
 * {@code OrderId} (a string — NOT a UUID) plus an {@code Orders[]} array describing each leg of a
 * (possibly multi-leg) order; on a rejection it returns an {@code ErrorInfo} object
 * ({@code ErrorCode} + {@code Message}). The {@code OrderId} string is what the adapter persists
 * against our internal {@link com.quorumtrading.model.valueobject.OrderId} (UUID) so order status /
 * cancel can round-trip (SAXO_ANSWERS §2.7).</p>
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} keeps the DTO tolerant of the many fields
 * Saxo returns that the adapter does not consume.</p>
 *
 * <p><b>⚠️ TODO(SB4, unconfirmed):</b> whether Saxo echoes a top-level {@code Status}/{@code FillPrice}
 * on the synchronous placement response (vs requiring a follow-up {@code GET /port/v1/orders}) is not
 * pinned down — the adapter treats a returned {@code OrderId} with no {@code ErrorInfo} as accepted
 * and maps a fill from any {@code Status}/price fields that ARE present, defaulting to PENDING.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaxoOrderResponse {

    /** Saxo broker-side order id (string, not a UUID). */
    @JsonProperty("OrderId")
    private String orderId;

    /** Our correlation id echoed back (the {@code ExternalReference} we sent). */
    @JsonProperty("ExternalReference")
    private String externalReference;

    /** Per-leg order details (multi-leg orders); usually a single leg for a simple equity order. */
    @JsonProperty("Orders")
    private List<SaxoOrderLeg> orders;

    /** Coarse order state when present, e.g. {@code Filled}, {@code Working}. */
    @JsonProperty("Status")
    private String status;

    /** Average fill price when filled (present on a status read). */
    @JsonProperty("FillPrice")
    private BigDecimal fillPrice;

    /** Filled share count (present on a status read). */
    @JsonProperty("FilledAmount")
    private BigDecimal filledAmount;

    /** Structured rejection info when the order did not go through. */
    @JsonProperty("ErrorInfo")
    private SaxoErrorInfo errorInfo;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public List<SaxoOrderLeg> getOrders() {
        return orders;
    }

    public void setOrders(List<SaxoOrderLeg> orders) {
        this.orders = orders;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getFillPrice() {
        return fillPrice;
    }

    public void setFillPrice(BigDecimal fillPrice) {
        this.fillPrice = fillPrice;
    }

    public BigDecimal getFilledAmount() {
        return filledAmount;
    }

    public void setFilledAmount(BigDecimal filledAmount) {
        this.filledAmount = filledAmount;
    }

    public SaxoErrorInfo getErrorInfo() {
        return errorInfo;
    }

    public void setErrorInfo(SaxoErrorInfo errorInfo) {
        this.errorInfo = errorInfo;
    }

    /**
     * Structured Saxo error: a {@code ErrorCode} + human-readable {@code Message}. Present on a
     * rejected placement (and on a per-leg failure inside {@link SaxoOrderLeg}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaxoErrorInfo {

        @JsonProperty("ErrorCode")
        private String errorCode;

        @JsonProperty("Message")
        private String message;

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        /** A compact {@code code: message} string for a {@link com.quorumtrading.model.broker.TradeResult} rejection reason. */
        public String describe() {
            if (errorCode == null && message == null) {
                return "unknown Saxo error";
            }
            if (errorCode == null) {
                return message;
            }
            if (message == null) {
                return errorCode;
            }
            return errorCode + ": " + message;
        }
    }

    /**
     * One leg of a placed order. A leg can itself carry an {@code ErrorInfo} when only part of a
     * multi-leg order failed; for a simple equity order there is one leg echoing the {@code OrderId}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaxoOrderLeg {

        @JsonProperty("OrderId")
        private String orderId;

        @JsonProperty("ErrorInfo")
        private SaxoErrorInfo errorInfo;

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public SaxoErrorInfo getErrorInfo() {
            return errorInfo;
        }

        public void setErrorInfo(SaxoErrorInfo errorInfo) {
            this.errorInfo = errorInfo;
        }
    }
}
