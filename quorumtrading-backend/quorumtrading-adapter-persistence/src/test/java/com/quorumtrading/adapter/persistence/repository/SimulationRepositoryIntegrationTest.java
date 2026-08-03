package com.quorumtrading.adapter.persistence.repository;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for SimulationRepository.
 *
 * Tests CRUD operations and query methods with real database.
 * Following the test diamond strategy: Integration test (60-70% of suite).
 */
@QuarkusTest
class SimulationRepositoryIntegrationTest {

    @Inject
    SimulationRepository simulationRepository;

    @Inject
    StrategyRepository strategyRepository;

    @Inject
    com.quorumtrading.application.port.out.SimulationLockPort simulationLockPort;

    // Note: Using PostgreSQL with transactional tests
    // Each test gets rolled back automatically, ensuring clean state

    @Test
    @Transactional
    @DisplayName("shouldSaveAndRetrieveSimulation_WhenValidSimulationProvided")
    void shouldSaveAndRetrieveSimulation_WhenValidSimulationProvided() {
        // Arrange
        Simulation simulation = createTestSimulation();

        // Act
        Simulation saved = simulationRepository.save(simulation);
        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getSimulationId()).isEqualTo(simulation.getSimulationId());
        assertThat(retrieved.get().getStrategyId()).isEqualTo(simulation.getStrategyId());
        assertThat(retrieved.get().getStartDate()).isEqualTo(simulation.getStartDate());
        assertThat(retrieved.get().getEndDate()).isEqualTo(simulation.getEndDate());
        // Use isEqualByComparingTo for BigDecimal comparison (ignores scale differences like 100000 vs 100000.0)
        assertThat(retrieved.get().getInitialCash().amount()).isEqualByComparingTo(simulation.getInitialCash().amount());
        assertThat(retrieved.get().getInitialCash().currency()).isEqualTo(simulation.getInitialCash().currency());
        assertThat(retrieved.get().getStatus()).isEqualTo(simulation.getStatus());
        assertThat(retrieved.get().getProgress()).isEqualTo(simulation.getProgress());
    }

    @Test
    @DisplayName("shouldReturnEmpty_WhenSimulationNotFound")
    void shouldReturnEmpty_WhenSimulationNotFound() {
        // Arrange
        SimulationId nonExistentId = new SimulationId(UUID.randomUUID());

        // Act
        Optional<Simulation> result = simulationRepository.findById(nonExistentId);

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldDeleteSimulation_WhenSimulationExists")
    void shouldDeleteSimulation_WhenSimulationExists() {
        // Arrange
        Simulation simulation = createTestSimulation();
        Simulation saved = simulationRepository.save(simulation);

        // Act
        boolean deleted = simulationRepository.deleteById(saved.getSimulationId());
        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());

        // Assert
        assertThat(deleted).isTrue();
        assertThat(retrieved).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("shouldFindAllSimulations_WhenMultipleSimulationsExist")
    void shouldFindAllSimulations_WhenMultipleSimulationsExist() {
        // Arrange
        Simulation sim1 = createTestSimulation();
        Simulation sim2 = createTestSimulation();
        Simulation sim3 = createTestSimulation();

        simulationRepository.save(sim1);
        simulationRepository.save(sim2);
        simulationRepository.save(sim3);

        // Act
        List<Simulation> allSimulations = simulationRepository.findAll();

        // Assert
        // Note: Using hasSizeGreaterThanOrEqualTo instead of exact size
        // because tests run against shared PostgreSQL database
        assertThat(allSimulations).hasSizeGreaterThanOrEqualTo(3);
        assertThat(allSimulations).extracting(Simulation::getSimulationId)
            .contains(sim1.getSimulationId(), sim2.getSimulationId(), sim3.getSimulationId());
    }

    @Test
    @Transactional
    @DisplayName("shouldUpdateSimulation_WhenSimulationExists")
    void shouldUpdateSimulation_WhenSimulationExists() {
        // Arrange
        Simulation original = createTestSimulation();
        Simulation saved = simulationRepository.save(original);

        // Create updated version with same ID but different status and progress
        Simulation updated = new Simulation(
            saved.getSimulationId(),
            saved.getName(),
            saved.getStrategyId(),
            saved.getStartDate(),
            saved.getEndDate(),
            saved.getInitialCash()
        );
        updated.setOwnerUserId(saved.getOwnerUserId());  // preserve owner — DB enforces NOT NULL
        updated.setStatus(SimulationStatus.COMPLETED);  // Changed from PENDING
        updated.setCompletedAt(Instant.now());  // Mark as completed
        updated.setProgress(100);  // Changed from 0

        // Act
        simulationRepository.save(updated);
        Optional<Simulation> afterSave = simulationRepository.findById(saved.getSimulationId());

        // Assert: save() writes the ordinary fields but NOT lifecycle state. This used to write
        // status and completedAt too, which is how a run's terminal state could be set by one
        // writer and silently reverted by the next routine save from a stale in-memory entity.
        assertThat(afterSave).isPresent();
        assertThat(afterSave.get().getProgress()).isEqualTo(100);
        assertThat(afterSave.get().getStatus())
            .as("save() must not move the lifecycle — that is updateStatus()'s job")
            .isEqualTo(SimulationStatus.PENDING);
        assertThat(afterSave.get().getCompletedAt()).isNull();

        // The transition goes through the narrow compare-and-set instead.
        boolean won = simulationRepository.updateStatus(saved.getSimulationId(),
            SimulationStatus.PENDING, SimulationStatus.COMPLETED, Instant.now(), null);
        Optional<Simulation> afterTransition = simulationRepository.findById(saved.getSimulationId());

        assertThat(won).isTrue();
        assertThat(afterTransition).isPresent();
        assertThat(afterTransition.get().getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(afterTransition.get().getCompletedAt()).isNotNull();
        assertThat(afterTransition.get().getSimulationId()).isEqualTo(saved.getSimulationId());
    }

    @Test
    @Transactional
    @DisplayName("save() must not overwrite a status written out of band")
    void saveMustNotOverwriteStatusWrittenOutOfBand() {
        // The exact revert that produced nine ghost completions in forty minutes: a watchdog moved
        // the row to a terminal state, and ~15 seconds later the executor's routine per-evaluation
        // save() copied "SIMULATING, completedAt=null" back off an entity it had been holding for
        // hours. Whether an out-of-band transition survived was decided by a race rather than a
        // rule. @Version did not help — save() re-reads the managed row inside its own transaction
        // and copies onto it, so the version check always passed.
        Simulation simulation = createTestSimulation();
        simulation.setStatus(SimulationStatus.SIMULATING);
        Simulation saved = simulationRepository.save(simulation);

        // Another writer ends the run.
        simulationRepository.updateStatus(saved.getSimulationId(),
            SimulationStatus.SIMULATING, SimulationStatus.COMPLETED, Instant.now(), null);

        // The long-lived in-memory entity still believes it is running, and saves again.
        simulationRepository.save(simulation);

        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus())
            .as("the out-of-band transition must survive the next routine save")
            .isEqualTo(SimulationStatus.COMPLETED);
        assertThat(retrieved.get().getCompletedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("updateStatus is a compare-and-set: a stale writer loses and is told so")
    void updateStatusIsCompareAndSet() {
        Simulation simulation = createTestSimulation();
        simulation.setStatus(SimulationStatus.SIMULATING);
        Simulation saved = simulationRepository.save(simulation);

        boolean first = simulationRepository.updateStatus(saved.getSimulationId(),
            SimulationStatus.SIMULATING, SimulationStatus.COMPLETED, Instant.now(), null);
        // A second writer still working from the SIMULATING view.
        boolean second = simulationRepository.updateStatus(saved.getSimulationId(),
            SimulationStatus.SIMULATING, SimulationStatus.FAILED, Instant.now(), "should not land");

        assertThat(first).isTrue();
        assertThat(second).as("the stale writer must lose").isFalse();

        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(retrieved.get().getFailureReason()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("updateProgress touches only progress")
    void updateProgressTouchesOnlyProgress() {
        Simulation simulation = createTestSimulation();
        simulation.setStatus(SimulationStatus.SIMULATING);
        Simulation saved = simulationRepository.save(simulation);

        simulationRepository.updateProgress(saved.getSimulationId(), 61);

        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getProgress()).isEqualTo(61);
        assertThat(retrieved.get().getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(retrieved.get().getCompletedAt()).isNull();
    }

    @Test
    @Transactional
    @DisplayName("markStarted stamps startedAt only while the row is still PENDING")
    void markStartedIsCompareAndSetOnPending() {
        Simulation simulation = createTestSimulation();
        Simulation saved = simulationRepository.save(simulation);
        Instant startedAt = Instant.now();

        boolean first = simulationRepository.markStarted(
            saved.getSimulationId(), SimulationStatus.SIMULATING, startedAt);
        boolean second = simulationRepository.markStarted(
            saved.getSimulationId(), SimulationStatus.LIVE, startedAt.plusSeconds(60));

        assertThat(first).isTrue();
        assertThat(second).as("startedAt is written once; a second start must not re-stamp it").isFalse();

        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(retrieved.get().getStartedAt()).isNotNull();
    }

    @Test
    @Transactional
    @DisplayName("findExpiredSimulations returns LIVE rows only, never a running backtest")
    void findExpiredSimulationsIsLiveOnly() {
        Simulation backtest = createTestSimulation();       // endDate in the past by construction
        backtest.setStatus(SimulationStatus.SIMULATING);
        simulationRepository.save(backtest);

        List<Simulation> expired = simulationRepository.findExpiredSimulations(Instant.now());

        assertThat(expired)
            .as("a SIMULATING backtest is 'expired' from the moment it starts — that says nothing "
                + "about whether it has finished, and completing it on that basis is the bug")
            .extracting(Simulation::getSimulationId)
            .doesNotContain(backtest.getSimulationId());
    }

    @Test
    @Transactional
    @DisplayName("findRunnable excludes both user-paused and system-paused rows")
    void findRunnableExcludesPausedRows() {
        // A freshly inserted row is paused=true (simulations start paused; StartSimulationService
        // clears it through updatePauseState, which is the only writer of those columns).
        Simulation runnable = createTestSimulation();
        runnable.setStatus(SimulationStatus.SIMULATING);
        simulationRepository.save(runnable);
        simulationRepository.updatePauseState(runnable.getSimulationId(), false, false, null);

        Simulation userPaused = createTestSimulation();
        userPaused.setStatus(SimulationStatus.SIMULATING);
        simulationRepository.save(userPaused);
        simulationRepository.updatePauseState(userPaused.getSimulationId(), true, false, null);

        // The system-paused shape: paused stays FALSE, only the external flag is set. This is the
        // row every scheduler used to keep re-offering forever.
        Simulation systemPaused = createTestSimulation();
        systemPaused.setStatus(SimulationStatus.SIMULATING);
        simulationRepository.save(systemPaused);
        simulationRepository.updatePauseState(
            systemPaused.getSimulationId(), false, true, "EXECUTION_STALLED");

        List<Simulation> found = simulationRepository.findRunnable(SimulationStatus.SIMULATING);

        assertThat(found).extracting(Simulation::getSimulationId)
            .contains(runnable.getSimulationId())
            .doesNotContain(userPaused.getSimulationId(), systemPaused.getSimulationId());
    }

    @Test
    @Transactional
    @DisplayName("findStalledBacktests: only a dead lease AND no recent progress qualifies")
    void findStalledBacktestsMatrix() {
        // No decisions exist for any of these, so the progress signal falls back to started_at.
        Instant longAgo = Instant.now().minus(10, ChronoUnit.HOURS);
        Duration leaseStale = Duration.ofMinutes(20);
        Duration stallGrace = Duration.ofMinutes(30);

        // (a) unlocked + started long ago ⇒ stalled. Note the explicit unpause: a row is inserted
        // paused=true, and the detector must never see a paused row, so every case below that is
        // meant to be *eligible* has to be unpaused first.
        Simulation unlockedAndOld = createTestSimulation();
        simulationRepository.save(unlockedAndOld);
        simulationRepository.updatePauseState(unlockedAndOld.getSimulationId(), false, false, null);
        simulationRepository.markStarted(
            unlockedAndOld.getSimulationId(), SimulationStatus.SIMULATING, longAgo);

        // (b) same, but paused by the user ⇒ never touched
        Simulation pausedAndOld = createTestSimulation();
        simulationRepository.save(pausedAndOld);
        simulationRepository.markStarted(
            pausedAndOld.getSimulationId(), SimulationStatus.SIMULATING, longAgo);
        simulationRepository.updatePauseState(pausedAndOld.getSimulationId(), true, false, null);

        // (c) same, but already system-paused ⇒ no double-kill
        Simulation externallyPausedAndOld = createTestSimulation();
        simulationRepository.save(externallyPausedAndOld);
        simulationRepository.markStarted(
            externallyPausedAndOld.getSimulationId(), SimulationStatus.SIMULATING, longAgo);
        simulationRepository.updatePauseState(
            externallyPausedAndOld.getSimulationId(), false, true, "DATA_GAP_DETECTED");

        // (d) old, but a live lease ⇒ an executor is on it, so it is not stalled however old
        Simulation leasedAndOld = createTestSimulation();
        simulationRepository.save(leasedAndOld);
        simulationRepository.updatePauseState(leasedAndOld.getSimulationId(), false, false, null);
        simulationRepository.markStarted(
            leasedAndOld.getSimulationId(), SimulationStatus.SIMULATING, longAgo);
        simulationLockPort.tryLock(leasedAndOld.getSimulationId(), "test-instance", leaseStale);

        // (e) unlocked but only just started ⇒ inside the grace, not stalled
        Simulation unlockedAndFresh = createTestSimulation();
        simulationRepository.save(unlockedAndFresh);
        simulationRepository.updatePauseState(unlockedAndFresh.getSimulationId(), false, false, null);
        simulationRepository.markStarted(
            unlockedAndFresh.getSimulationId(), SimulationStatus.SIMULATING, Instant.now());

        List<Simulation> stalled =
            simulationRepository.findStalledBacktests(leaseStale, stallGrace);

        assertThat(stalled).extracting(Simulation::getSimulationId)
            .contains(unlockedAndOld.getSimulationId())
            .doesNotContain(
                pausedAndOld.getSimulationId(),
                externallyPausedAndOld.getSimulationId(),
                leasedAndOld.getSimulationId(),
                unlockedAndFresh.getSimulationId());
    }

    @Test
    @Transactional
    @DisplayName("shouldHandleDifferentCurrencies_WhenSavingSimulations")
    void shouldHandleDifferentCurrencies_WhenSavingSimulations() {
        // Arrange - Create strategies first for referential integrity
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        Strategy strategyUsd = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "USD Strategy",
            "## USD Test\nTest strategy for USD",
            systemUserId
        );
        Strategy savedStrategyUsd = strategyRepository.save(strategyUsd);

        Strategy strategyEur = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "EUR Strategy",
            "## EUR Test\nTest strategy for EUR",
            systemUserId
        );
        Strategy savedStrategyEur = strategyRepository.save(strategyEur);

        // Create simulations with different currencies
        Simulation usdSimulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "USD Test Simulation",
            savedStrategyUsd.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(10000), Money.Currency.USD)
        );
        usdSimulation.setOwnerUserId(systemUserId);

        Simulation eurSimulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "EUR Test Simulation",
            savedStrategyEur.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(8000), Money.Currency.EUR)
        );
        eurSimulation.setOwnerUserId(systemUserId);

        // Act
        simulationRepository.save(usdSimulation);
        simulationRepository.save(eurSimulation);

        Optional<Simulation> retrievedUsd = simulationRepository.findById(usdSimulation.getSimulationId());
        Optional<Simulation> retrievedEur = simulationRepository.findById(eurSimulation.getSimulationId());

        // Assert
        assertThat(retrievedUsd).isPresent();
        assertThat(retrievedUsd.get().getInitialCash().currency()).isEqualTo(Money.Currency.USD);
        assertThat(retrievedUsd.get().getInitialCash().amount()).isEqualByComparingTo(BigDecimal.valueOf(10000));

        assertThat(retrievedEur).isPresent();
        assertThat(retrievedEur.get().getInitialCash().currency()).isEqualTo(Money.Currency.EUR);
        assertThat(retrievedEur.get().getInitialCash().amount()).isEqualByComparingTo(BigDecimal.valueOf(8000));
    }

    @Test
    @Transactional
    @DisplayName("shouldPreserveDatePrecision_WhenSavingSimulation")
    void shouldPreserveDatePrecision_WhenSavingSimulation() {
        // Arrange - Create strategy first
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "Date Test Strategy",
            "## Date Precision Test\nTest strategy",
            systemUserId
        );
        Strategy savedStrategy = strategyRepository.save(strategy);

        Instant now = Instant.now();
        Instant startDate = now.minus(30, ChronoUnit.DAYS);
        Instant endDate = now;

        Simulation simulation = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Date Precision Test Simulation",
            savedStrategy.getId(),
            startDate,
            endDate,
            new Money(BigDecimal.valueOf(10000), Money.Currency.USD)
        );
        simulation.setOwnerUserId(systemUserId);

        // Act
        Simulation saved = simulationRepository.save(simulation);
        Optional<Simulation> retrieved = simulationRepository.findById(saved.getSimulationId());

        // Assert
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getStartDate()).isEqualTo(startDate);
        assertThat(retrieved.get().getEndDate()).isEqualTo(endDate);
    }

    @Test
    @Transactional
    @DisplayName("shouldReloadForceCompletedLiveSimulation_WhenEndDateIsNull")
    void shouldReloadForceCompletedLiveSimulation_WhenEndDateIsNull() {
        // Arrange: LIVE simulation (endDate == null), force-completed before any end date
        Simulation live = createLiveTestSimulation(null);
        live.start();
        assertThat(live.getStatus()).isEqualTo(SimulationStatus.LIVE);
        live.forceComplete();
        int progressAtEnd = live.getProgress();
        simulationRepository.save(live);

        // Act: reload through the repository (mapper.toDomain round-trip)
        Optional<Simulation> reloaded = simulationRepository.findById(live.getSimulationId());

        // Assert: must not throw and must carry the persisted terminal state
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(reloaded.get().getEndDate()).isNull();
        assertThat(reloaded.get().getCompletedAt()).isNotNull();
        // Force-ending preserves how far the run actually got; it does not claim 100%.
        assertThat(reloaded.get().getProgress()).isEqualTo(progressAtEnd);
        assertThat(reloaded.get().isPaused()).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("shouldReloadForceCompletedLiveSimulation_WhenEndDateIsInFuture")
    void shouldReloadForceCompletedLiveSimulation_WhenEndDateIsInFuture() {
        // Arrange: LIVE simulation whose scheduled end date is in the future
        Simulation live = createLiveTestSimulation(Instant.now().plus(30, ChronoUnit.DAYS));
        live.start();
        assertThat(live.getStatus()).isEqualTo(SimulationStatus.LIVE);
        live.forceComplete();
        int progressAtEnd = live.getProgress();
        simulationRepository.save(live);

        // Act
        Optional<Simulation> reloaded = simulationRepository.findById(live.getSimulationId());

        // Assert
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(reloaded.get().getCompletedAt()).isNotNull();
        assertThat(reloaded.get().getProgress()).isEqualTo(progressAtEnd);
    }

    // Helper: LIVE simulation (startDate today, endDate null or in the future)
    private Simulation createLiveTestSimulation(Instant endDate) {
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "Live Test Strategy",
            "## Test\nSimple live test strategy",
            systemUserId
        );
        Strategy savedStrategy = strategyRepository.save(strategy);

        Simulation sim = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Live Test Simulation",
            savedStrategy.getId(),
            Instant.now(),
            endDate,
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        sim.setOwnerUserId(systemUserId);
        return sim;
    }

    // Helper method to create test simulation with a valid strategy reference
    private Simulation createTestSimulation() {
        // Create and save a strategy first to ensure referential integrity
        UserId systemUserId = new UserId(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        Strategy strategy = new Strategy(
            new StrategyId(UUID.randomUUID()),
            "Test Strategy",
            "## Test\nSimple test strategy",
            systemUserId
        );
        Strategy savedStrategy = strategyRepository.save(strategy);

        Simulation sim = new Simulation(
            new SimulationId(UUID.randomUUID()),
            "Test Simulation",
            savedStrategy.getId(),
            Instant.now().minus(30, ChronoUnit.DAYS),
            Instant.now(),
            new Money(BigDecimal.valueOf(100000), Money.Currency.USD)
        );
        sim.setOwnerUserId(systemUserId);
        return sim;
    }
}
