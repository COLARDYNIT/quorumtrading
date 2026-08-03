package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.adapter.persistence.entity.SimulationJpaEntity;
import com.quorumtrading.adapter.persistence.entity.StrategyJpaEntity;
import com.quorumtrading.adapter.persistence.mapper.SimulationMapper;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.ChallengeId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA EntityManager-based implementation of the SimulationRepository port.
 *
 * This adapter bridges the domain port interface to the JPA persistence layer.
 * Now that database uses UUID primary keys, mapping is direct between domain and database.
 *
 * Uses EntityManager instead of Panache static methods to support cross-module entity discovery in Quarkus.
 */
@ApplicationScoped
@Transactional(Transactional.TxType.REQUIRED)
public class SimulationRepositoryImpl implements SimulationRepository {

    private static final Logger log = LoggerFactory.getLogger(SimulationRepositoryImpl.class);

    @Inject
    SimulationMapper mapper;

    @Inject
    EntityManager entityManager;

    @Override
    @Transactional
    public Simulation save(Simulation simulation) {
        log.debug("save simulation: {}", simulation);
        SimulationJpaEntity entity = mapper.toJpaEntity(simulation);

        // Check if this is an update (entity with this ID exists)
        if (simulation.getSimulationId() != null) {
            UUID uuid = simulation.getSimulationId().value();
            SimulationJpaEntity existing = entityManager.find(SimulationJpaEntity.class, uuid);

            if (existing != null) {
                // Update existing simulation
                existing.setName(entity.getName());
                existing.setStrategyId(entity.getStrategyId());
                existing.setStartDate(entity.getStartDate());
                existing.setEndDate(entity.getEndDate());
                // status, startedAt, completedAt and failureReason are managed via
                // updateStatus() / markStarted() — NOT copied here. Same reason as the pause
                // columns below, but this one has a body count: the executor holds one
                // Simulation instance for the whole run, so a per-evaluation save() wrote its
                // hours-old "SIMULATING, completedAt=null" back over whatever had been written
                // in the meantime. A watchdog force-completed a healthy run nine times in forty
                // minutes and was silently reverted nine times, leaking a broker capital release
                // and a completion event on each pass; a run whose executor was hung never saved,
                // so its kill stuck and was reported as a clean 100% COMPLETED. Whether a
                // transition survived was decided by a race rather than a rule (ADR-0004).
                // @Version does not protect this: we re-read `existing` inside our own
                // transaction and copy onto it, so the version check always passes.
                // paused, pausedForExternalReason, externalPauseReason are managed via
                // updatePauseState() to avoid stale overwrites from in-memory entities
                // held during long-running simulation loops
                existing.setProgress(entity.getProgress());
                // evaluation counters are deliberately NOT copied here. They are managed via
                // updateEvaluationCounters() for the same reason as the pause fields: the executor
                // holds one Simulation instance for the whole run, so copying counters from it
                // would overwrite increments made concurrently through the circuit breaker.
                // Copy broker account fields (FIX: these were missing, causing brokerAccountId to be lost)
                existing.setBrokerAccountId(entity.getBrokerAccountId());
                existing.setTradingLimitAmount(entity.getTradingLimitAmount());
                existing.setTradingLimitCurrency(entity.getTradingLimitCurrency());
                // benchmark_id and challenge_id are managed via updateBenchmarkId()/updateChallengeId()
                // to avoid stale overwrites from in-memory entities held during long-running simulation loops
                existing.setInitialPortfolio(entity.getInitialPortfolio());
                existing.setConfiguration(entity.getConfiguration());
                entity = entityManager.merge(existing);
            } else {
                // New simulation with pre-set ID
                entityManager.persist(entity);
            }
        } else {
            // New simulation without ID
            entityManager.persist(entity);
        }

        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Simulation> findById(SimulationId id) {
        UUID uuid = id.value();

        return Optional.ofNullable(entityManager.find(SimulationJpaEntity.class, uuid))
            .map(mapper::toDomain);
    }

    @Override
    public List<Simulation> findByStrategyId(StrategyId strategyId) {
        // Direct UUID mapping - database now uses UUID primary keys
        UUID uuid = strategyId.value();

        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s WHERE s.strategyId = :strategyId",
            SimulationJpaEntity.class
        );
        query.setParameter("strategyId", uuid);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findAll() {
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s",
            SimulationJpaEntity.class
        );

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean deleteById(SimulationId id) {
        UUID uuid = id.value();

        SimulationJpaEntity entity = entityManager.find(SimulationJpaEntity.class, uuid);
        if (entity != null) {
            entityManager.remove(entity);
            return true;
        }
        return false;
    }

    @Override
    public List<Simulation> findExpiredSimulations(java.time.Instant cutoffTime) {
        // LIVE only. A backtest's endDate is in the past by definition, so including SIMULATING
        // here returned every running backtest as "expired" from the moment it started.
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s " +
            "WHERE s.endDate <= :cutoffTime " +
            "AND s.status = 'LIVE'",
            SimulationJpaEntity.class
        );
        query.setParameter("cutoffTime", cutoffTime);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findStalledBacktests(java.time.Duration leaseStaleAfter,
                                                 java.time.Duration stallGrace) {
        // Native SQL, and every time comparison is against the database's NOW(): locked_at is
        // `timestamp without time zone`, so comparing it to a Java Instant would resolve through
        // the JVM's zone and behave differently on a laptop than in a UTC container (ADR-0003).
        // The interval is built the same way SimulationLockAdapter builds its staleness window.
        //
        // COALESCE(newest decision, started_at) is the progress signal: a run that has produced
        // no decision at all is measured from when it started. If both are NULL the comparison is
        // NULL and the row is excluded — a run that never started is not a stalled one.
        @SuppressWarnings("unchecked")
        List<SimulationJpaEntity> rows = entityManager.createNativeQuery(
            "SELECT s.* FROM simulations s "
                + "WHERE s.status = 'SIMULATING' "
                + "AND s.end_date IS NOT NULL "
                + "AND s.paused = false "
                + "AND s.paused_for_external_reason = false "
                + "AND (s.locked_by IS NULL "
                + "     OR s.locked_at < NOW() - CAST(:leaseSeconds || ' seconds' AS INTERVAL)) "
                + "AND COALESCE("
                + "      (SELECT max(c.created_at) FROM consensus_decisions c "
                + "       WHERE c.simulation_id = s.id), s.started_at"
                + "    ) < NOW() - CAST(:stallSeconds || ' seconds' AS INTERVAL)",
            SimulationJpaEntity.class)
            .setParameter("leaseSeconds", String.valueOf(leaseStaleAfter.toSeconds()))
            .setParameter("stallSeconds", String.valueOf(stallGrace.toSeconds()))
            .getResultList();

        return rows.stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findRunnable(com.quorumtrading.model.enums.SimulationStatus status) {
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s "
                + "WHERE s.status = :status "
                + "AND s.paused = false "
                + "AND s.pausedForExternalReason = false "
                + "ORDER BY s.createdAt ASC",
            SimulationJpaEntity.class
        );
        query.setParameter("status", status.name());

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findWithFilters(
            Optional<com.quorumtrading.model.enums.SimulationStatus> status,
            Optional<StrategyId> strategyId,
            Optional<java.time.Instant> since,
            Optional<Integer> limit) {

        // Build dynamic query
        StringBuilder jpql = new StringBuilder("SELECT s FROM SimulationJpaEntity s WHERE 1=1");

        if (status.isPresent()) {
            jpql.append(" AND s.status = :status");
        }
        if (strategyId.isPresent()) {
            jpql.append(" AND s.strategyId = :strategyId");
        }
        if (since.isPresent()) {
            jpql.append(" AND s.startedAt >= :since");
        }

        jpql.append(" ORDER BY s.startedAt DESC");

        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(jpql.toString(), SimulationJpaEntity.class);

        // Set parameters
        if (status.isPresent()) {
            query.setParameter("status", status.get().name());
        }
        if (strategyId.isPresent()) {
            query.setParameter("strategyId", strategyId.get().value());
        }
        if (since.isPresent()) {
            query.setParameter("since", since.get());
        }
        if (limit.isPresent()) {
            query.setMaxResults(limit.get());
        }

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findByBenchmarkId(BenchmarkId benchmarkId) {
        UUID uuid = benchmarkId.value();

        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s WHERE s.benchmarkId = :benchmarkId ORDER BY s.createdAt ASC",
            SimulationJpaEntity.class
        );
        query.setParameter("benchmarkId", uuid);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findPausedSimulations() {
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s WHERE s.paused = true ORDER BY s.createdAt DESC",
            SimulationJpaEntity.class
        );

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateCreatedAt(SimulationId id, java.time.Instant newCreatedAt) {
        UUID uuid = id.value();
        SimulationJpaEntity entity = entityManager.find(SimulationJpaEntity.class, uuid);

        if (entity == null) {
            throw new java.util.NoSuchElementException("Simulation not found: " + uuid);
        }

        entity.setCreatedAt(newCreatedAt);
        entityManager.merge(entity);
    }

    @Override
    @Transactional
    public void updateBenchmarkId(SimulationId id, BenchmarkId benchmarkId) {
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET benchmark_id = :benchmarkId WHERE id = :id")
            .setParameter("benchmarkId", benchmarkId != null ? benchmarkId.value() : null)
            .setParameter("id", id.value())
            .executeUpdate();

        if (updated == 0) {
            throw new java.util.NoSuchElementException("Simulation not found: " + id.value());
        }
    }

    @Override
    public List<Simulation> findByChallengeId(ChallengeId challengeId) {
        UUID uuid = challengeId.value();

        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s WHERE s.challengeId = :challengeId ORDER BY s.createdAt ASC",
            SimulationJpaEntity.class
        );
        query.setParameter("challengeId", uuid);

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Simulation> findByOwnerIdAndChallengeId(UserId ownerId, ChallengeId challengeId) {
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s JOIN StrategyJpaEntity st ON s.strategyId = st.id " +
            "WHERE st.ownerId = :ownerId AND s.challengeId = :challengeId",
            SimulationJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());
        query.setParameter("challengeId", challengeId.value());

        return query.getResultList().stream()
            .findFirst()
            .map(mapper::toDomain);
    }

    @Override
    @Transactional
    public void updateChallengeId(SimulationId id, ChallengeId challengeId) {
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET challenge_id = :challengeId WHERE id = :id")
            .setParameter("challengeId", challengeId != null ? challengeId.value() : null)
            .setParameter("id", id.value())
            .executeUpdate();

        if (updated == 0) {
            throw new java.util.NoSuchElementException("Simulation not found: " + id.value());
        }
    }

    @Override
    @Transactional
    public void updateEvaluationCounters(SimulationId id, int totalEvaluationCount,
                                         int skippedEvaluationCount, int totalSkippedEvaluationCount) {
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET total_evaluation_count = :total, skipped_evaluation_count = :skipped, "
                + "total_skipped_evaluation_count = :totalSkipped WHERE id = :id")
            .setParameter("total", totalEvaluationCount)
            .setParameter("skipped", skippedEvaluationCount)
            .setParameter("totalSkipped", totalSkippedEvaluationCount)
            .setParameter("id", id.value())
            .executeUpdate();

        if (updated == 0) {
            throw new java.util.NoSuchElementException("Simulation not found: " + id.value());
        }
    }

    @Override
    @Transactional
    public void updatePauseState(SimulationId id, boolean paused, boolean pausedForExternalReason, String externalPauseReason) {
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET paused = :paused, paused_for_external_reason = :pausedExt, external_pause_reason = :reason WHERE id = :id")
            .setParameter("paused", paused)
            .setParameter("pausedExt", pausedForExternalReason)
            .setParameter("reason", externalPauseReason)
            .setParameter("id", id.value())
            .executeUpdate();

        if (updated == 0) {
            throw new java.util.NoSuchElementException("Simulation not found: " + id.value());
        }
    }

    /**
     * Drop any managed copy of this row so a later read in the same transaction sees the write.
     *
     * <p>A native UPDATE goes round the persistence context, so the first-level cache keeps
     * serving the pre-update instance to anything that calls {@code findById} before the
     * transaction ends. For a repository whose whole purpose is a narrow out-of-band write, that
     * is a stale read waiting to happen — and stale reads of lifecycle state are the failure this
     * class of method exists to prevent.</p>
     */
    private void evictManagedCopy(SimulationId id) {
        SimulationJpaEntity cached = entityManager.find(SimulationJpaEntity.class, id.value());
        if (cached != null) {
            entityManager.detach(cached);
        }
    }

    @Override
    @Transactional
    public void updateProgress(SimulationId id, int progress) {
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET progress = :progress WHERE id = :id")
            .setParameter("progress", progress)
            .setParameter("id", id.value())
            .executeUpdate();

        if (updated == 0) {
            throw new java.util.NoSuchElementException("Simulation not found: " + id.value());
        }
        evictManagedCopy(id);
    }

    @Override
    @Transactional
    public boolean updateStatus(SimulationId id,
                                com.quorumtrading.model.enums.SimulationStatus expected,
                                com.quorumtrading.model.enums.SimulationStatus next,
                                java.time.Instant completedAt,
                                String failureReason) {
        // WHERE status = :expected makes this a compare-and-set: a caller holding a stale view of
        // the row writes nothing and is told so, rather than winning by writing last.
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET status = :next, completed_at = :completedAt, "
                + "failure_reason = :failureReason WHERE id = :id AND status = :expected")
            .setParameter("next", next.name())
            .setParameter("completedAt", completedAt)
            .setParameter("failureReason", failureReason)
            .setParameter("id", id.value())
            .setParameter("expected", expected.name())
            .executeUpdate();

        if (updated == 0) {
            log.debug("Simulation {} not transitioned {} -> {}: row is no longer in the expected "
                + "status (transitioned elsewhere, or deleted)", id.value(), expected, next);
            return false;
        }
        evictManagedCopy(id);
        return true;
    }

    @Override
    @Transactional
    public boolean markStarted(SimulationId id,
                               com.quorumtrading.model.enums.SimulationStatus next,
                               java.time.Instant startedAt) {
        int updated = entityManager.createNativeQuery(
            "UPDATE simulations SET status = :next, started_at = :startedAt "
                + "WHERE id = :id AND status = 'PENDING'")
            .setParameter("next", next.name())
            .setParameter("startedAt", startedAt)
            .setParameter("id", id.value())
            .executeUpdate();

        if (updated == 0) {
            log.debug("Simulation {} not started: row is no longer PENDING", id.value());
            return false;
        }
        evictManagedCopy(id);
        return true;
    }

    @Override
    public List<Simulation> findByOwnerId(UserId ownerId) {
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s " +
            "JOIN StrategyJpaEntity st ON s.strategyId = st.id " +
            "WHERE st.ownerId = :ownerId " +
            "ORDER BY s.createdAt DESC",
            SimulationJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findRunningByOwnerId(UserId ownerId) {
        // Join with strategies to filter by owner
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s " +
            "JOIN StrategyJpaEntity st ON s.strategyId = st.id " +
            "WHERE st.ownerId = :ownerId " +
            "AND s.status IN ('SIMULATING', 'LIVE')",
            SimulationJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findPausedForExternalReasonByOwnerId(UserId ownerId) {
        // Join with strategies to filter by owner
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s " +
            "JOIN StrategyJpaEntity st ON s.strategyId = st.id " +
            "WHERE st.ownerId = :ownerId " +
            "AND s.pausedForExternalReason = true",
            SimulationJpaEntity.class
        );
        query.setParameter("ownerId", ownerId.value());

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public List<Simulation> findActiveByBrokerAccountId(BrokerAccountId brokerAccountId) {
        TypedQuery<SimulationJpaEntity> query = entityManager.createQuery(
            "SELECT s FROM SimulationJpaEntity s WHERE s.brokerAccountId = :brokerAccountId " +
            "AND s.status NOT IN ('COMPLETED', 'FAILED')",
            SimulationJpaEntity.class
        );
        query.setParameter("brokerAccountId", brokerAccountId.value());

        return query.getResultList()
            .stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }

    @Override
    public long count() {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(s) FROM SimulationJpaEntity s",
            Long.class
        );
        return query.getSingleResult();
    }

    @Override
    public Map<String, Long> countByStatus() {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager.createQuery(
            "SELECT s.status, COUNT(s) FROM SimulationJpaEntity s GROUP BY s.status"
        ).getResultList();

        Map<String, Long> statusCounts = new HashMap<>();
        for (Object[] row : results) {
            statusCounts.put((String) row[0], (Long) row[1]);
        }
        return statusCounts;
    }
}
