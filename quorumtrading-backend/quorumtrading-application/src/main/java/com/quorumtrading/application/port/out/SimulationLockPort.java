package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Duration;

/**
 * Port for distributed simulation locking.
 * Replaces the in-memory ConcurrentHashMap in SimulationExecutor
 * with database-backed locking for horizontal scaling.
 */
public interface SimulationLockPort {

    /**
     * Attempt to acquire an exclusive lock on a simulation.
     * Uses SELECT FOR UPDATE SKIP LOCKED to avoid blocking.
     *
     * @param id the simulation ID to lock
     * @param instanceId the ECS task/instance identifier acquiring the lock
     * @param timeout duration after which the lock is considered stale
     * @return true if the lock was acquired, false if already locked
     */
    boolean tryLock(SimulationId id, String instanceId, Duration timeout);

    /**
     * Renew the lease on a lock this instance already holds (heartbeat).
     * Long-running backtests (slow local LLM engines) can outlive the acquisition
     * timeout; without renewal a concurrent scheduler resume steals the lock and a
     * second executor loop runs the same simulation in parallel.
     *
     * @param id the simulation ID whose lock to renew
     * @param instanceId the instance that believes it holds the lock
     * @return true if the lease was renewed, false if this instance no longer holds the lock
     */
    boolean refresh(SimulationId id, String instanceId);

    /**
     * Release the lock on a simulation.
     * Only succeeds if the lock is held by the specified instance.
     *
     * @param id the simulation ID to unlock
     * @param instanceId the instance releasing the lock
     */
    void unlock(SimulationId id, String instanceId);

    /**
     * Force-unlock all simulations whose locks are older than the given duration.
     * Used by a scheduled cleanup job to recover from crashed instances.
     *
     * @param staleDuration locks older than this duration are forcefully released
     * @return the number of stale locks cleaned up
     */
    int forceUnlockStale(Duration staleDuration);
}
