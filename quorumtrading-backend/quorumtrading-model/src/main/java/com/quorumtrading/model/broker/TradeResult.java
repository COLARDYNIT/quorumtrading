package com.quorumtrading.model.broker;

import com.quorumtrading.model.enums.TradeStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OrderId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a trade execution attempt.
 * Contains order details, execution status, and optional rejection reason.
 */
public record TradeResult(
        TradeStatus status,
        OrderId orderId,
        Money executionPrice,
        BigDecimal filledQuantity,
        String rejectionReason,
        Instant executedAt
) {

    public TradeResult {
        Objects.requireNonNull(status, "TradeStatus cannot be null");
        Objects.requireNonNull(orderId, "OrderId cannot be null");
        Objects.requireNonNull(executedAt, "ExecutedAt cannot be null");

        if (status == TradeStatus.EXECUTED || status == TradeStatus.PARTIAL_FILL) {
            Objects.requireNonNull(executionPrice, "ExecutionPrice cannot be null for executed trades");
            Objects.requireNonNull(filledQuantity, "FilledQuantity cannot be null for executed trades");
        }

        if (status == TradeStatus.REJECTED) {
            Objects.requireNonNull(rejectionReason, "RejectionReason cannot be null for rejected trades");
        }
    }

    /**
     * Get the rejection reason as an Optional.
     */
    public Optional<String> getRejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    /**
     * Check if the trade was successfully executed (fully or partially).
     */
    public boolean isSuccessful() {
        return status == TradeStatus.EXECUTED || status == TradeStatus.PARTIAL_FILL;
    }

    /**
     * Check if the trade was rejected.
     */
    public boolean isRejected() {
        return status == TradeStatus.REJECTED;
    }

    /**
     * Create a successful execution result.
     */
    public static TradeResult executed(
            OrderId orderId,
            Money executionPrice,
            BigDecimal filledQuantity,
            Instant executedAt) {
        return new TradeResult(
                TradeStatus.EXECUTED,
                orderId,
                executionPrice,
                filledQuantity,
                null,
                executedAt
        );
    }

    /**
     * Create a partial fill result.
     */
    public static TradeResult partialFill(
            OrderId orderId,
            Money executionPrice,
            BigDecimal filledQuantity,
            Instant executedAt) {
        return new TradeResult(
                TradeStatus.PARTIAL_FILL,
                orderId,
                executionPrice,
                filledQuantity,
                null,
                executedAt
        );
    }

    /**
     * Create a rejected trade result.
     */
    public static TradeResult rejected(
            OrderId orderId,
            String rejectionReason,
            Instant rejectedAt) {
        return new TradeResult(
                TradeStatus.REJECTED,
                orderId,
                null,
                null,
                rejectionReason,
                rejectedAt
        );
    }
}
