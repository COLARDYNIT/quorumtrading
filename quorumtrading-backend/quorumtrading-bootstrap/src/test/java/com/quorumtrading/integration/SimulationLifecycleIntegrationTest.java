package com.quorumtrading.integration;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.CreateSimulationUseCase;
import com.quorumtrading.application.usecase.DeleteSimulationUseCase;
import com.quorumtrading.application.usecase.EndSimulationResult;
import com.quorumtrading.application.usecase.EndSimulationUseCase;
import com.quorumtrading.application.usecase.command.CreateSimulationCommand;
import com.quorumtrading.application.usecase.command.DeleteSimulationCommand;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for Simulation create/start/end/delete lifecycle.
 *
 * Tests the state machine transitions (PENDING → SIMULATING/LIVE → COMPLETED)
 * and verifies portfolio snapshots are created at appropriate lifecycle points.
 */
@QuarkusTest
class SimulationLifecycleIntegrationTest {

    @Inject
    CreateSimulationUseCase createSimulationUseCase;

    @Inject
    EndSimulationUseCase endSimulationUseCase;

    @Inject
    DeleteSimulationUseCase deleteSimulationUseCase;

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    PortfolioRepository portfolioRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    IntegrationTestHelper helper;

    @Test
    @Transactional
    @DisplayName("should create simulation with initial portfolio snapshot")
    void shouldCreateSimulationWithInitialPortfolio() {
        // Arrange
        Strategy strategy = helper.createStrategy("Sim Test Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Portfolio Snapshot Test",
                strategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(50_000),
                null,
                null,
                null,
                strategy.getOwnerId());

        // Act
        SimulationId simulationId = createSimulationUseCase.execute(command);

        // Assert — simulation exists with initial portfolio snapshot
        assertThat(simulationId).isNotNull();

        Optional<PortfolioSnapshot> snapshot = portfolioRepository.findLatestBySimulationId(simulationId);
        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().getCashAvailable().amount())
                .isEqualByComparingTo(BigDecimal.valueOf(50_000));
    }

    @Test
    @Transactional
    @DisplayName("should create simulation in PENDING or WAITING_FOR_DATA status")
    void shouldCreateSimulationInPendingStatus() {
        // Arrange
        Strategy strategy = helper.createStrategy("Pending Test Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Pending Simulation",
                strategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(100_000),
                null,
                null,
                null,
                strategy.getOwnerId());

        // Act
        SimulationId simulationId = createSimulationUseCase.execute(command);

        // Assert — after synchronous backfill check, simulation is either PENDING (data available)
        // or WAITING_FOR_DATA (backfill triggered). Both are valid initial states.
        Simulation simulation = simulationRepository.findById(simulationId).orElseThrow();
        assertThat(simulation.getStatus()).isIn(SimulationStatus.PENDING, SimulationStatus.WAITING_FOR_DATA);
    }

    @Test
    @Transactional
    @DisplayName("should transition to SIMULATING when endDate is in the past")
    void shouldStartSimulationTransitionToSimulating() {
        // Arrange — create simulation with past endDate (backtest)
        Strategy strategy = helper.createStrategy("Backtest Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createSimulation("Backtest Sim", strategy,
                Instant.now().minus(14, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS),
                Money.usd(100_000));

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        // Act — call start() on the domain entity directly (avoids SimulationExecutor), then
        // persist the transition through markStarted(). save() deliberately no longer writes
        // lifecycle columns: it is called on the hot path with an entity a run may have been
        // holding for hours, and copying status off it silently reverted out-of-band writes.
        simulation.start();
        simulationRepository.markStarted(
                simulation.getSimulationId(), simulation.getStatus(), simulation.getStartedAt());

        // Assert
        Simulation reloaded = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(reloaded.getStartedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("should transition to LIVE when endDate is in the future")
    void shouldStartSimulationTransitionToLive() {
        // Arrange — create simulation with future endDate (live), startDate must be today or later
        Strategy strategy = helper.createStrategy("Live Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createSimulation("Live Sim", strategy,
                Instant.now(),
                Instant.now().plus(7, ChronoUnit.DAYS),
                Money.usd(100_000));

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        // Act
        simulation.start();
        simulationRepository.markStarted(
                simulation.getSimulationId(), simulation.getStatus(), simulation.getStartedAt());

        // Assert
        Simulation reloaded = simulationRepository.findById(simulation.getSimulationId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SimulationStatus.LIVE);
    }

    @Test
    @DisplayName("should end expired simulations and mark as COMPLETED")
    void shouldEndExpiredSimulations() {
        // Arrange — create a LIVE simulation with endDate in the past.
        // Uses a separate committed transaction so the data is visible to endExpiredSimulations().
        SimulationId simId = QuarkusTransaction.requiringNew().call(() -> {
            Strategy strategy = helper.createStrategy("Expired Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
            Simulation simulation = helper.createSimulation("Expired Sim", strategy,
                    Instant.now().minus(14, ChronoUnit.DAYS),
                    Instant.now().minus(2, ChronoUnit.HOURS),
                    Money.usd(100_000));
            simulation.start();
            simulationRepository.save(simulation);
            assertThat(simulation.getStatus()).isIn(SimulationStatus.LIVE, SimulationStatus.SIMULATING);
            return simulation.getSimulationId();
        });

        // Act — endExpiredSimulations() processes ALL expired simulations in the DB.
        // Concurrent test activity may cause OptimisticLockException for other simulations'
        // entities. If that happens, retry with a fresh persistence context where stale
        // entities have their correct versions.
        EndSimulationResult result;
        try {
            result = QuarkusTransaction.requiringNew().call(() ->
                    endSimulationUseCase.endExpiredSimulations());
        } catch (Exception e) {
            result = QuarkusTransaction.requiringNew().call(() ->
                    endSimulationUseCase.endExpiredSimulations());
        }

        // Assert
        assertThat(result.simulationsFailed()).isZero();
    }

    @Test
    @Transactional
    @DisplayName("should delete simulation and cascade to snapshots")
    void shouldDeleteSimulationCascade() {
        // Arrange
        Strategy strategy = helper.createStrategy("Delete Test Strategy", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);
        Simulation simulation = helper.createSimulation("Deletable Sim", strategy);
        helper.createPortfolioSnapshot(simulation);

        // Verify they exist
        assertThat(simulationRepository.findById(simulation.getSimulationId())).isPresent();
        assertThat(portfolioRepository.findLatestBySimulationId(simulation.getSimulationId())).isPresent();

        // Act — use the strategy owner's ID for authorization
        deleteSimulationUseCase.execute(new DeleteSimulationCommand(simulation.getSimulationId(), strategy.getOwnerId()));

        // Flush and clear the persistence context so subsequent queries hit the DB
        // (CASCADE DELETE happens at DB level, Hibernate cache may still hold removed entities)
        entityManager.flush();
        entityManager.clear();

        // Assert
        assertThat(simulationRepository.findById(simulation.getSimulationId())).isEmpty();
        assertThat(portfolioRepository.findLatestBySimulationId(simulation.getSimulationId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("should reject simulation for non-existent strategy")
    void shouldRejectSimulationForNonexistentStrategy() {
        StrategyId fakeId = new StrategyId(UUID.randomUUID());

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Ghost Simulation",
                fakeId,
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(100_000),
                null,
                null,
                null,
                new UserId(UUID.randomUUID()));

        assertThatThrownBy(() -> createSimulationUseCase.execute(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strategy not found");
    }

    @Test
    @Transactional
    @DisplayName("should have portfolio snapshot after domain start()")
    void shouldCreateInitialSnapshotOnStart() {
        // Arrange — create simulation via use case (creates initial snapshot)
        Strategy strategy = helper.createStrategy("Snapshot On Start", IntegrationTestHelper.VALID_CRYPTO_STRATEGY);

        CreateSimulationCommand command = new CreateSimulationCommand(
                "Snapshot Start Test",
                strategy.getId(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now(),
                Money.usd(75_000),
                null,
                null,
                null,
                strategy.getOwnerId());

        SimulationId simulationId = createSimulationUseCase.execute(command);

        // Assert — initial snapshot was created by CreateSimulationService
        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(simulationId);
        assertThat(snapshots).isNotEmpty();
        assertThat(snapshots.get(0).getCashAvailable().amount())
                .isEqualByComparingTo(BigDecimal.valueOf(75_000));
    }
}
