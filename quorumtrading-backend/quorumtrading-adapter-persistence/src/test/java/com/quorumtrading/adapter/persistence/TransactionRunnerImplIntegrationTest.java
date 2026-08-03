package com.quorumtrading.adapter.persistence;

import com.quorumtrading.application.port.out.TransactionRunner;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The boundary an application-layer POJO borrows to make two repository writes atomic.
 *
 * <p>Its whole value is the rollback, so that is what is asserted here against a real database
 * rather than a stub: work done before a failure must not survive it. The spend ledger depends on
 * this — a budget charged for a call whose ledger row was rolled back is a user billed for
 * something nothing records.</p>
 */
@QuarkusTest
@DisplayName("TransactionRunner — an adapter-owned transaction boundary")
class TransactionRunnerImplIntegrationTest {

    @Inject
    TransactionRunner transactionRunner;

    @Inject
    EntityManager em;

    @Test
    @DisplayName("returns the unit of work's result")
    void returnsTheResult() {
        assertThat(transactionRunner.inTransaction(() -> "done")).isEqualTo("done");
    }

    @Test
    @DisplayName("a failure propagates and rolls back everything written before it")
    void failurePropagatesAndRollsBack() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> transactionRunner.inTransaction(() -> {
            em.createNativeQuery("INSERT INTO users (id, email) VALUES (:id, :email)")
                .setParameter("id", userId)
                .setParameter("email", "rollback-" + userId + "@test.local")
                .executeUpdate();
            throw new IllegalStateException("second write failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(countUsers(userId))
            .as("the first write must not survive the second one's failure")
            .isZero();
    }

    @Transactional
    long countUsers(UUID userId) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM users WHERE id = :id")
            .setParameter("id", userId)
            .getSingleResult()).longValue();
    }
}
