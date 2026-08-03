package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.BrokerTokenLeasePort;
import com.quorumtrading.application.port.out.BrokerTokenLeasePort.RefreshLease;
import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.model.entity.BrokerAccount;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.enums.SaxoEnvironment;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SaxoOAuthCredentials;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency integration test for {@link BrokerTokenLeaseAdapter} on {@code quorumtrading_test}.
 *
 * <p>The whole point of the lease is mutual exclusion of Saxo refresh-token rotation across
 * instances. These tests prove that the atomic {@code UPDATE ... WHERE} on the V117 lease columns
 * grants the lease to exactly ONE of two concurrent acquirers, and that {@code forceReleaseStale}
 * recovers a lease abandoned by a crashed holder.</p>
 */
@QuarkusTest
class BrokerTokenLeaseAdapterIntegrationTest {

    @Inject
    BrokerTokenLeasePort leaseAdapter;

    @Inject
    BrokerAccountRepository brokerAccountRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    EntityManager entityManager;

    private BrokerAccountId createSaxoAccount() {
        UserId userId = UserId.generate();
        User user = User.create(
            userId,
            "lease-test-" + UUID.randomUUID() + "@example.com",
            "Lease Test",
            null,
            OAuthProvider.GOOGLE);
        userRepository.save(user);

        SaxoOAuthCredentials creds = new SaxoOAuthCredentials(
            "app-key", "app-secret", "refresh-1", "access-1",
            Instant.parse("2026-06-04T12:00:00Z"), SaxoEnvironment.SIM);
        Money balance = new Money(new BigDecimal("1000.00"), Money.Currency.EUR);
        BrokerAccount account = BrokerAccount.createReal(
            userId, "Saxo Lease", BrokerType.SAXO_SIM, creds, balance, balance);
        return brokerAccountRepository.save(account).getId();
    }

    @Test
    void twoConcurrentAcquirers_exactlyOneWins() throws Exception {
        BrokerAccountId accountId = createSaxoAccount();
        Duration lease = Duration.ofMinutes(5);

        // Two threads race on acquire(), released together by a barrier to maximise contention.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier startGate = new CyclicBarrier(2);
        AtomicInteger winners = new AtomicInteger(0);

        Callable<Boolean> race = () -> {
            startGate.await();
            return leaseAdapter.acquire(accountId, Thread.currentThread().getName(), lease).isPresent();
        };

        try {
            Future<Boolean> a = pool.submit(race);
            Future<Boolean> b = pool.submit(race);
            if (a.get(30, TimeUnit.SECONDS)) {
                winners.incrementAndGet();
            }
            if (b.get(30, TimeUnit.SECONDS)) {
                winners.incrementAndGet();
            }
        } finally {
            pool.shutdownNow();
        }

        // Exactly one of the two callers may rotate the account's tokens.
        assertThat(winners.get()).isEqualTo(1);

        // And the row reflects a single held lease.
        assertThat(lockedBy(accountId.value())).isNotNull();
    }

    @Test
    void manyConcurrentAcquirers_exactlyOneWins() throws Exception {
        BrokerAccountId accountId = createSaxoAccount();
        Duration lease = Duration.ofMinutes(5);
        int threads = 8;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier startGate = new CyclicBarrier(threads);
        AtomicInteger winners = new AtomicInteger(0);

        Callable<Boolean> race = () -> {
            startGate.await();
            return leaseAdapter.acquire(accountId, Thread.currentThread().getName(), lease).isPresent();
        };

        try {
            List<Future<Boolean>> futures = pool.invokeAll(
                java.util.Collections.nCopies(threads, race), 30, TimeUnit.SECONDS);
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    winners.incrementAndGet();
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners.get()).isEqualTo(1);
    }

    @Test
    void release_freesLeaseForNextAcquirer() {
        BrokerAccountId accountId = createSaxoAccount();
        Duration lease = Duration.ofMinutes(5);

        RefreshLease held = leaseAdapter.acquire(accountId, "holder-1", lease).orElseThrow();
        // A second acquirer is blocked while the lease is live.
        assertThat(leaseAdapter.acquire(accountId, "holder-2", lease)).isEmpty();

        leaseAdapter.release(held);
        assertThat(lockedBy(accountId.value())).isNull();

        // After release the lease is grantable again.
        assertThat(leaseAdapter.acquire(accountId, "holder-2", lease)).isPresent();
        assertThat(lockedBy(accountId.value())).isEqualTo("holder-2");
    }

    @Test
    void release_byNonHolder_isNoOpAndLeaveLeaseHeld() {
        BrokerAccountId accountId = createSaxoAccount();
        Duration lease = Duration.ofMinutes(5);

        leaseAdapter.acquire(accountId, "real-holder", lease).orElseThrow();

        // Releasing with a token for a different holder must not free the lease.
        leaseAdapter.release(new RefreshLease(accountId, "impostor"));
        assertThat(lockedBy(accountId.value())).isEqualTo("real-holder");
    }

    @Test
    void forceReleaseStale_clearsLeaseOlderThanMaxAge() {
        BrokerAccountId accountId = createSaxoAccount();

        // Stand in for a crashed holder: a held lease whose timestamp is well in the past.
        seedLease(accountId.value(), "crashed-task", Instant.now().minus(Duration.ofMinutes(30)));

        boolean cleared = leaseAdapter.forceReleaseStale(accountId, Duration.ofMinutes(10));
        assertThat(cleared).isTrue();
        assertThat(lockedBy(accountId.value())).isNull();
    }

    @Test
    void forceReleaseStale_doesNotClearFreshLease() {
        BrokerAccountId accountId = createSaxoAccount();

        // A lease younger than maxAge must survive a stale sweep.
        leaseAdapter.acquire(accountId, "fresh-holder", Duration.ofMinutes(5)).orElseThrow();

        boolean cleared = leaseAdapter.forceReleaseStale(accountId, Duration.ofMinutes(10));
        assertThat(cleared).isFalse();
        assertThat(lockedBy(accountId.value())).isEqualTo("fresh-holder");
    }

    @Test
    void forceReleaseStale_onUnlockedAccount_returnsFalse() {
        BrokerAccountId accountId = createSaxoAccount();

        boolean cleared = leaseAdapter.forceReleaseStale(accountId, Duration.ofMinutes(10));
        assertThat(cleared).isFalse();
    }

    private String lockedBy(UUID accountId) {
        return QuarkusTransaction.requiringNew().call(() ->
            (String) entityManager.createNativeQuery(
                    "SELECT refresh_locked_by FROM broker_accounts WHERE id = :id")
                .setParameter("id", accountId)
                .getSingleResult());
    }

    /**
     * Writes the lease columns directly on the row with a controllable acquisition time, standing
     * in for a crashed holder that never released. Runs in its own committed transaction.
     */
    private void seedLease(UUID accountId, String holder, Instant lockedAt) {
        QuarkusTransaction.requiringNew().run(() ->
            entityManager.createNativeQuery(
                    "UPDATE broker_accounts SET refresh_locked_by = :holder, refresh_locked_at = :lockedAt " +
                    "WHERE id = :id")
                .setParameter("holder", holder)
                .setParameter("lockedAt", lockedAt)
                .setParameter("id", accountId)
                .executeUpdate());
    }
}
