package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a payment transaction.
 * Tracks payments made through external payment providers (e.g., Stripe).
 */
public class PaymentTransaction {

    /**
     * Payment transaction status.
     */
    public enum Status {
        PENDING,
        SUCCEEDED,
        FAILED,
        CANCELED,
        REFUNDED
    }

    private UUID id;
    private UserId userId;
    private String providerTransactionId; // e.g., Stripe checkout session ID or payment intent ID
    private String provider; // e.g., "stripe"
    private BigDecimal amount;
    private String currency;
    private Status status;
    private String description;
    private Instant createdAt;
    private Instant completedAt;
    private String metadata; // JSON string for provider-specific data

    /**
     * Default constructor for reconstruction from persistence.
     */
    public PaymentTransaction() {
    }

    /**
     * Create a new pending payment transaction.
     */
    public static PaymentTransaction createPending(
            UserId userId,
            String provider,
            String providerTransactionId,
            BigDecimal amount,
            String currency,
            String description
    ) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.id = UUID.randomUUID();
        tx.userId = Objects.requireNonNull(userId, "userId is required");
        tx.provider = Objects.requireNonNull(provider, "provider is required");
        tx.providerTransactionId = Objects.requireNonNull(providerTransactionId, "providerTransactionId is required");
        tx.amount = Objects.requireNonNull(amount, "amount is required");
        tx.currency = Objects.requireNonNull(currency, "currency is required");
        tx.description = description;
        tx.status = Status.PENDING;
        tx.createdAt = Instant.now();
        return tx;
    }

    /**
     * Mark the payment as succeeded.
     */
    public void markAsSucceeded() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Cannot mark non-pending payment as succeeded: " + this.status);
        }
        this.status = Status.SUCCEEDED;
        this.completedAt = Instant.now();
    }

    /**
     * Mark the payment as failed.
     */
    public void markAsFailed() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Cannot mark non-pending payment as failed: " + this.status);
        }
        this.status = Status.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * Mark the payment as canceled.
     */
    public void markAsCanceled() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Cannot mark non-pending payment as canceled: " + this.status);
        }
        this.status = Status.CANCELED;
        this.completedAt = Instant.now();
    }

    /**
     * Mark the payment as refunded.
     */
    public void markAsRefunded() {
        if (this.status != Status.SUCCEEDED) {
            throw new IllegalStateException("Cannot refund non-succeeded payment: " + this.status);
        }
        this.status = Status.REFUNDED;
    }

    public boolean isSucceeded() {
        return this.status == Status.SUCCEEDED;
    }

    public boolean isPending() {
        return this.status == Status.PENDING;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getProviderTransactionId() {
        return providerTransactionId;
    }

    public String getProvider() {
        return provider;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getMetadata() {
        return metadata;
    }

    // Setters for reconstruction from persistence
    public void setId(UUID id) {
        this.id = id;
    }

    public void setUserId(UserId userId) {
        this.userId = userId;
    }

    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentTransaction that = (PaymentTransaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "PaymentTransaction{" +
                "id=" + id +
                ", userId=" + userId +
                ", provider='" + provider + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", status=" + status +
                '}';
    }
}
