package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.SimulationLockPort;
import com.quorumtrading.model.valueobject.SimulationId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Database-backed distributed simulation locking.
 * Uses PostgreSQL row-level locking (FOR UPDATE SKIP LOCKED) to ensure
 * only one ECS task executes a given simulation at a time.
 */
@ApplicationScoped
public class SimulationLockAdapter implements SimulationLockPort {

    private static final Logger log = LoggerFactory.getLogger(SimulationLockAdapter.class);

    @PersistenceContext
    EntityManager em;

    @Override
    @Transactional
    public boolean tryLock(SimulationId id, String instanceId, Duration timeout) {
        long staleSeconds = timeout.toSeconds();

        // Use database-side NOW() for both write and comparison to avoid JVM/DB timezone mismatch.
        // The locked_at column is TIMESTAMP (without timezone), so all comparisons must use NOW().
        //
        // "locked_by = :instanceId" lets the SAME instance re-acquire its own lease. Safe because
        // SimulationExecutor serializes same-JVM submissions in memory before ever calling this —
        // the clause exists for the crash case: an instance killed mid-run restarts with the same
        // hostname-derived id and would otherwise wait out the full stale window to resume its
        // own orphaned lease.
        int updated = em.createNativeQuery(
                "UPDATE simulations SET locked_by = :instanceId, locked_at = NOW() " +
                "WHERE id = :simId AND (locked_by IS NULL OR locked_by = :instanceId " +
                "OR locked_at < NOW() - CAST(:staleSeconds || ' seconds' AS INTERVAL))")
            .setParameter("instanceId", instanceId)
            .setParameter("simId", id.value())
            .setParameter("staleSeconds", String.valueOf(staleSeconds))
            .executeUpdate();

        if (updated > 0) {
            log.debug("Acquired lock on simulation {} for instance {}", shortId(id), instanceId);
            return true;
        }

        log.debug("Failed to acquire lock on simulation {} for instance {} (already locked)", shortId(id), instanceId);
        return false;
    }

    @Override
    @Transactional
    public boolean refresh(SimulationId id, String instanceId) {
        // Heartbeat: bump locked_at only while we still own the lock. If another instance
        // took over after our lease went stale, this matches 0 rows and the caller must stop.
        int updated = em.createNativeQuery(
                "UPDATE simulations SET locked_at = NOW() " +
                "WHERE id = :simId AND locked_by = :instanceId")
            .setParameter("simId", id.value())
            .setParameter("instanceId", instanceId)
            .executeUpdate();

        if (updated > 0) {
            log.debug("Renewed lock lease on simulation {} for instance {}", shortId(id), instanceId);
            return true;
        }
        log.warn("Lock lease on simulation {} no longer held by instance {} (taken over or released)",
            shortId(id), instanceId);
        return false;
    }

    @Override
    @Transactional
    public void unlock(SimulationId id, String instanceId) {
        int updated = em.createNativeQuery(
                "UPDATE simulations SET locked_by = NULL, locked_at = NULL " +
                "WHERE id = :simId AND locked_by = :instanceId")
            .setParameter("simId", id.value())
            .setParameter("instanceId", instanceId)
            .executeUpdate();

        if (updated > 0) {
            log.debug("Released lock on simulation {} for instance {}", shortId(id), instanceId);
        } else {
            log.warn("Could not release lock on simulation {} for instance {} (not held or already released)",
                shortId(id), instanceId);
        }
    }

    @Override
    @Transactional
    public int forceUnlockStale(Duration staleDuration) {
        long staleSeconds = staleDuration.toSeconds();

        // Use database-side NOW() to avoid JVM/DB timezone mismatch on TIMESTAMP columns
        int unlocked = em.createNativeQuery(
                "UPDATE simulations SET locked_by = NULL, locked_at = NULL " +
                "WHERE locked_by IS NOT NULL AND locked_at < NOW() - CAST(:staleSeconds || ' seconds' AS INTERVAL)")
            .setParameter("staleSeconds", String.valueOf(staleSeconds))
            .executeUpdate();

        if (unlocked > 0) {
            log.info("Force-unlocked {} stale simulation locks (older than {})", unlocked, staleDuration);
        }

        return unlocked;
    }

    private String shortId(SimulationId id) {
        return id.value().toString().substring(0, 8);
    }
}
