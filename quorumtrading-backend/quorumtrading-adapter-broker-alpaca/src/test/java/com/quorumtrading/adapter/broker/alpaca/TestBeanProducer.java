package com.quorumtrading.adapter.broker.alpaca;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test bean producer for Alpaca adapter integration tests.
 * Provides mock implementations of dependencies required by BrokerRouter.
 */
@Mock
@ApplicationScoped
public class TestBeanProducer implements SimulationRepository {

    /**
     * Mock implementation returns a LIVE simulation for testing.
     */
    @Override
    public Optional<Simulation> findById(SimulationId simulationId) {
        // Return a mock LIVE simulation for testing
        Simulation simulation = new Simulation(
            simulationId,
            "Test Simulation",
            new StrategyId(UUID.randomUUID()),
            Instant.now().minusSeconds(3600), // Started 1 hour ago
            Instant.now().plusSeconds(3600),  // Ends in 1 hour
            Money.usd(BigDecimal.valueOf(10000))
        );
        simulation.start(); // Transitions to LIVE mode
        return Optional.of(simulation);
    }

    @Override
    public Simulation save(Simulation simulation) {
        // Return the simulation unchanged for tests
        return simulation;
    }

    @Override
    public List<Simulation> findByStrategyId(StrategyId strategyId) {
        return List.of();
    }

    @Override
    public List<Simulation> findAll() {
        return List.of();
    }

    @Override
    public boolean deleteById(SimulationId id) {
        return true;
    }

    @Override
    public List<Simulation> findExpiredSimulations(Instant cutoffTime) {
        return List.of();
    }

    @Override
    public List<Simulation> findStalledBacktests(java.time.Duration leaseStaleAfter,
                                                 java.time.Duration stallGrace) {
        return List.of();
    }

    @Override
    public List<Simulation> findRunnable(SimulationStatus status) {
        return List.of();
    }

    @Override
    public void updateProgress(SimulationId id, int progress) {
        // no-op stub
    }

    @Override
    public boolean updateStatus(SimulationId id, SimulationStatus expected,
                                SimulationStatus next, Instant completedAt, String failureReason) {
        return true;
    }

    @Override
    public boolean markStarted(SimulationId id, SimulationStatus next, Instant startedAt) {
        return true;
    }

    @Override
    public List<Simulation> findWithFilters(
        Optional<SimulationStatus> status,
        Optional<StrategyId> strategyId,
        Optional<Instant> since,
        Optional<Integer> limit
    ) {
        return List.of();
    }

    @Override
    public List<Simulation> findByBenchmarkId(BenchmarkId benchmarkId) {
        return List.of();
    }

    @Override
    public List<Simulation> findPausedSimulations() {
        return List.of(); // Return empty list for tests
    }

    @Override
    public void updateCreatedAt(SimulationId id, Instant newCreatedAt) {
        // No-op for mock
    }

    @Override
    public List<Simulation> findPausedForExternalReasonByOwnerId(UserId ownerId) {
        return List.of();
    }

    @Override
    public List<Simulation> findRunningByOwnerId(UserId ownerId) {
        return List.of();
    }

    @Override
    public List<Simulation> findByOwnerId(UserId ownerId) {
        return List.of();
    }

    @Override
    public List<Simulation> findActiveByBrokerAccountId(BrokerAccountId brokerAccountId) {
        return List.of();
    }

    @Override
    public long count() {
        return 0L;
    }

    @Override
    public java.util.Map<String, Long> countByStatus() {
        return java.util.Collections.emptyMap();
    }

    @Override
    public void updateBenchmarkId(SimulationId id, BenchmarkId benchmarkId) {
        // No-op for mock
    }

    @Override
    public java.util.List<Simulation> findByChallengeId(com.quorumtrading.model.valueobject.ChallengeId challengeId) {
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.Optional<Simulation> findByOwnerIdAndChallengeId(com.quorumtrading.model.valueobject.UserId ownerId, com.quorumtrading.model.valueobject.ChallengeId challengeId) {
        return java.util.Optional.empty();
    }

    @Override
    public void updateChallengeId(SimulationId id, com.quorumtrading.model.valueobject.ChallengeId challengeId) {
        // No-op for mock
    }

    @Override
    public void updateEvaluationCounters(SimulationId id, int totalEvaluationCount,
            int skippedEvaluationCount, int totalSkippedEvaluationCount) {
        // no-op
    }

    @Override
    public void updatePauseState(SimulationId id, boolean paused, boolean pausedForExternalReason, String externalPauseReason) {
        // No-op for mock
    }
}
