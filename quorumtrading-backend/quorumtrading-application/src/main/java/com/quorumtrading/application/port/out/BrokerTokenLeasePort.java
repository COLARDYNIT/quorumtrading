package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.BrokerAccountId;

import java.time.Duration;
import java.util.Optional;

/**
 * Out-port for a per-account refresh lease that serializes token rotation across instances.
 *
 * <p>Saxo's refresh token rotates on every use: if two ECS tasks refresh the same account
 * concurrently, one rotation invalidates the other's token and breaks the chain. This port is the
 * correctness boundary that prevents that — exactly one holder may rotate an account's tokens at a
 * time. Implementations back it with an atomic {@code UPDATE ... WHERE} on the broker-account row
 * (the {@code refresh_locked_by} / {@code refresh_locked_at} columns from V117), the same pattern
 * as the existing simulation lock.</p>
 */
public interface BrokerTokenLeasePort {

    /**
     * Attempts to acquire the refresh lease for an account.
     *
     * <p>Succeeds only if no live lease is currently held (or the existing lease is older than the
     * implementation's staleness window). The returned token, when present, must be passed back to
     * {@link #release} to free the lease; the caller that fails to acquire must NOT refresh.</p>
     *
     * @param brokerAccountId the account to lease (non-null)
     * @param holderId        a stable identifier for the acquiring instance/thread (non-blank)
     * @param leaseDuration   how long the lease remains valid before it may be force-released (non-null)
     * @return a lease token if acquired, or empty if another holder owns a live lease
     */
    Optional<RefreshLease> acquire(BrokerAccountId brokerAccountId, String holderId, Duration leaseDuration);

    /**
     * Releases a previously acquired lease.
     *
     * <p>A no-op (or safe) if the lease has already been force-released or expired, so it is safe to
     * call from a {@code finally} block.</p>
     *
     * @param lease the lease token returned by {@link #acquire} (non-null)
     */
    void release(RefreshLease lease);

    /**
     * Force-releases a lease that has been held longer than {@code maxAge}, regardless of holder.
     *
     * <p>Used to recover from a crashed holder that never released its lease, so refresh can resume.
     * Implementations clear the lock columns when {@code refresh_locked_at} is older than
     * {@code maxAge}.</p>
     *
     * @param brokerAccountId the account whose stale lease should be cleared (non-null)
     * @param maxAge          the minimum age a lease must exceed to be considered stale (non-null)
     * @return {@code true} if a stale lease was found and released, {@code false} otherwise
     */
    boolean forceReleaseStale(BrokerAccountId brokerAccountId, Duration maxAge);

    /**
     * Opaque handle representing a held refresh lease, returned by {@link #acquire} and passed back
     * to {@link #release}.
     *
     * @param brokerAccountId the leased account
     * @param holderId        the identifier of the holder that acquired the lease
     */
    record RefreshLease(BrokerAccountId brokerAccountId, String holderId) {
        public RefreshLease {
            if (brokerAccountId == null) {
                throw new IllegalArgumentException("brokerAccountId cannot be null");
            }
            if (holderId == null || holderId.isBlank()) {
                throw new IllegalArgumentException("holderId cannot be null or blank");
            }
        }
    }
}
