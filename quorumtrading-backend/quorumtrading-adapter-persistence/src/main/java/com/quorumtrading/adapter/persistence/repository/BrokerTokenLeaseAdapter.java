package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.BrokerTokenLeasePort;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

/**
 * Database-backed per-account refresh lease for Saxo OAuth token rotation.
 *
 * <p>Saxo's refresh token rotates on every use: if two ECS tasks refresh the same account
 * concurrently, one rotation invalidates the other's token and breaks the chain. This adapter is the
 * correctness boundary that prevents that — exactly one holder may rotate an account's tokens at a
 * time.</p>
 *
 * <p>It uses the same atomic {@code UPDATE ... WHERE} pattern as {@link SimulationLockAdapter}, on
 * the V117 {@code refresh_locked_by} / {@code refresh_locked_at} columns of {@code broker_accounts}.
 * Because the conditional update is a single statement evaluated under the row lock, exactly one of
 * two concurrent acquirers can satisfy the {@code WHERE} predicate and observe a non-zero update
 * count — the other sees the row already locked and fails to acquire.</p>
 *
 * <p>All timestamp reads and writes use the database-side {@code NOW()} so comparisons are immune to
 * JVM/DB clock skew, exactly as the simulation lock does.</p>
 */
@ApplicationScoped
public class BrokerTokenLeaseAdapter implements BrokerTokenLeasePort {

    private static final Logger log = LoggerFactory.getLogger(BrokerTokenLeaseAdapter.class);

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public Optional<RefreshLease> acquire(BrokerAccountId brokerAccountId, String holderId, Duration leaseDuration) {
        if (brokerAccountId == null) {
            throw new IllegalArgumentException("brokerAccountId cannot be null");
        }
        if (holderId == null || holderId.isBlank()) {
            throw new IllegalArgumentException("holderId cannot be null or blank");
        }
        if (leaseDuration == null) {
            throw new IllegalArgumentException("leaseDuration cannot be null");
        }

        long leaseSeconds = leaseDuration.toSeconds();

        // Atomic conditional update: only one of two concurrent callers can satisfy the predicate.
        // A lease is acquirable when the row is currently free (refresh_locked_by IS NULL) or the
        // existing lease has aged past the caller's leaseDuration window (a crashed holder).
        int updated = em.createNativeQuery(
                "UPDATE broker_accounts SET refresh_locked_by = :holderId, refresh_locked_at = NOW() " +
                "WHERE id = :accountId AND (refresh_locked_by IS NULL " +
                "OR refresh_locked_at < NOW() - CAST(:leaseSeconds || ' seconds' AS INTERVAL))")
            .setParameter("holderId", holderId)
            .setParameter("accountId", brokerAccountId.value())
            .setParameter("leaseSeconds", String.valueOf(leaseSeconds))
            .executeUpdate();

        if (updated > 0) {
            log.debug("Acquired refresh lease on broker account {} for holder {}", shortId(brokerAccountId), holderId);
            return Optional.of(new RefreshLease(brokerAccountId, holderId));
        }

        log.debug("Failed to acquire refresh lease on broker account {} for holder {} (already leased)",
            shortId(brokerAccountId), holderId);
        return Optional.empty();
    }

    @Override
    @Transactional
    public void release(RefreshLease lease) {
        if (lease == null) {
            throw new IllegalArgumentException("lease cannot be null");
        }

        int updated = em.createNativeQuery(
                "UPDATE broker_accounts SET refresh_locked_by = NULL, refresh_locked_at = NULL " +
                "WHERE id = :accountId AND refresh_locked_by = :holderId")
            .setParameter("accountId", lease.brokerAccountId().value())
            .setParameter("holderId", lease.holderId())
            .executeUpdate();

        if (updated > 0) {
            log.debug("Released refresh lease on broker account {} for holder {}",
                shortId(lease.brokerAccountId()), lease.holderId());
        } else {
            // Safe to call from a finally block: the lease may already be force-released or expired.
            log.debug("No refresh lease released on broker account {} for holder {} (not held or already freed)",
                shortId(lease.brokerAccountId()), lease.holderId());
        }
    }

    @Override
    @Transactional
    public boolean forceReleaseStale(BrokerAccountId brokerAccountId, Duration maxAge) {
        if (brokerAccountId == null) {
            throw new IllegalArgumentException("brokerAccountId cannot be null");
        }
        if (maxAge == null) {
            throw new IllegalArgumentException("maxAge cannot be null");
        }

        long maxAgeSeconds = maxAge.toSeconds();

        // Clear only a lease that is currently held AND older than maxAge — recovers from a crashed
        // holder. Database-side NOW() avoids JVM/DB timezone mismatch on the timestamp column.
        int unlocked = em.createNativeQuery(
                "UPDATE broker_accounts SET refresh_locked_by = NULL, refresh_locked_at = NULL " +
                "WHERE id = :accountId AND refresh_locked_by IS NOT NULL " +
                "AND refresh_locked_at < NOW() - CAST(:maxAgeSeconds || ' seconds' AS INTERVAL)")
            .setParameter("accountId", brokerAccountId.value())
            .setParameter("maxAgeSeconds", String.valueOf(maxAgeSeconds))
            .executeUpdate();

        if (unlocked > 0) {
            log.info("Force-released stale refresh lease on broker account {} (older than {})",
                shortId(brokerAccountId), maxAge);
            return true;
        }

        return false;
    }

    private String shortId(BrokerAccountId id) {
        return id.value().toString().substring(0, 8);
    }
}
