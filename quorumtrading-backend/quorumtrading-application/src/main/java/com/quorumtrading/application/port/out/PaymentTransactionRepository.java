package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.PaymentTransaction;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for PaymentTransaction persistence.
 */
public interface PaymentTransactionRepository {

    /**
     * Save a payment transaction.
     *
     * @param transaction The transaction to save
     * @return The saved transaction
     */
    PaymentTransaction save(PaymentTransaction transaction);

    /**
     * Find a transaction by its ID.
     *
     * @param id Transaction ID
     * @return The transaction if found
     */
    Optional<PaymentTransaction> findById(UUID id);

    /**
     * Find a transaction by provider transaction ID.
     *
     * @param providerTransactionId The provider's transaction ID (e.g., Stripe session ID)
     * @return The transaction if found
     */
    Optional<PaymentTransaction> findByProviderTransactionId(String providerTransactionId);

    /**
     * Find all transactions for a user.
     *
     * @param userId The user ID
     * @return List of transactions, most recent first
     */
    List<PaymentTransaction> findByUserId(UserId userId);

    /**
     * Find successful transactions for a user.
     *
     * @param userId The user ID
     * @return List of successful transactions
     */
    List<PaymentTransaction> findSuccessfulByUserId(UserId userId);

    /**
     * Count successful transactions.
     *
     * @return Total count of successful payments
     */
    long countSuccessful();

    /**
     * Delete all transactions for a user (GDPR account deletion).
     *
     * @param userId The user ID
     * @return Number of deleted transactions
     */
    int deleteByUserId(UserId userId);
}
