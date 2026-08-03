package com.quorumtrading.model.entity;

import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.*;

class SimulationTest {

    @Test
    void shouldCreateSimulationWithoutBenchmark() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // Future dates for LIVE mode
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        assertThat(simulation.getSimulationId()).isEqualTo(simId);
        assertThat(simulation.getName()).isEqualTo(name);
        assertThat(simulation.getStrategyId()).isEqualTo(strategyId);
        assertThat(simulation.getStartDate()).isEqualTo(startDate);
        assertThat(simulation.getEndDate()).isEqualTo(endDate);
        assertThat(simulation.getInitialCash()).isEqualTo(initialCash);
        assertThat(simulation.getBenchmarkId()).isNull();
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);
        assertThat(simulation.getProgress()).isEqualTo(0);
        assertThat(simulation.isPaused()).isTrue();
        assertThat(simulation.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCreateSimulationWithBenchmark() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        BenchmarkId benchmarkId = BenchmarkId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // Future dates for LIVE mode
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash, benchmarkId);

        assertThat(simulation.getBenchmarkId()).isEqualTo(benchmarkId);
        assertThat(simulation.getSimulationId()).isEqualTo(simId);
        assertThat(simulation.getName()).isEqualTo(name);
    }

    @Test
    void shouldIdentifyBacktestWhenStartDateBeforeCreation() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Historical Backtest";
        Instant now = Instant.now();
        Instant startDate = now.minus(90, ChronoUnit.DAYS);  // Start date in the past
        Instant endDate = now.minus(30, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        // The simulation was just created (createdAt ~= now), but startDate is 90 days ago
        assertThat(simulation.isBacktest()).isTrue();
    }

    @Test
    void shouldNotIdentifyBacktestWhenEndDateIsNull() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Live Simulation";
        Instant now = Instant.now();
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        // LIVE simulations have endDate == null
        Simulation simulation = new Simulation(simId, name, strategyId, now, null, initialCash);

        assertThat(simulation.isBacktest()).isFalse();
    }

    @Test
    void shouldIdentifyBacktestWhenEndDateIsNearPresent() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Near-present Backtest";
        Instant now = Instant.now();
        Instant startDate = now.minus(30, ChronoUnit.DAYS);
        Instant endDate = now.minus(1, ChronoUnit.MINUTES); // endDate just before now
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        // Any simulation with an endDate is a backtest
        assertThat(simulation.isBacktest()).isTrue();
    }

    @Test
    void shouldStartSimulationInSimulatingModeForHistoricalEndDate() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Historical Backtest";
        Instant now = Instant.now();
        Instant startDate = now.minus(90, ChronoUnit.DAYS);
        Instant endDate = now.minus(30, ChronoUnit.DAYS);  // endDate in the past = BACKTEST
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(simulation.isPaused()).isFalse();
        assertThat(simulation.getStartedAt()).isNotNull();
    }

    @Test
    void shouldStartAsBacktestWhenStartDateIsInPastAndEndDateIsToday() {
        // Edge case: benchmark ending today — endDate is end of today, startDate is in the past
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Benchmark ending today";
        Instant now = Instant.now();
        // The last second of today, UTC — the boundary the entity judges against, so this holds
        // whatever hour the suite runs at and wherever it runs.
        Instant endOfToday = now.truncatedTo(ChronoUnit.DAYS)
            .plus(1, ChronoUnit.DAYS).minusSeconds(1);
        Instant startDate = now.minus(7, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endOfToday, initialCash);
        simulation.start();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(simulation.isPaused()).isFalse();
        assertThat(simulation.getStartedAt()).isNotNull();
    }

    @Test
    void shouldRejectBacktestWithEndDateInTheFuture() {
        // startDate yesterday + endDate tomorrow → rejected
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        Instant startDate = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(7, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        assertThatThrownBy(() -> new Simulation(simId, "Bad backtest", strategyId, startDate, endDate, initialCash))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Backtest cannot extend into the future");
    }

    @Test
    void shouldAllowBacktestWithStartAndEndBothInThePast() {
        // Both dates in the past → valid backtest
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        Instant startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, "Valid backtest", strategyId, startDate, endDate, initialCash);
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);
    }

    @Test
    void shouldAllowSimulationWithStartAndEndBothInTheFuture() {
        // Both dates in the future → valid LIVE simulation
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        Instant startDate = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(30, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, "Future sim", strategyId, startDate, endDate, initialCash);
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);
    }

    @Test
    void shouldAllowBacktestEndingToday() {
        // startDate yesterday, endDate end of today → valid (today is not "in the future")
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        Instant startDate = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant endOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS)
            .plus(1, ChronoUnit.DAYS).minusSeconds(1);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, "Ends today", strategyId, startDate, endOfToday, initialCash);
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);
    }

    @Test
    void shouldStartSimulationInLiveModeForFutureStartDate() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Future Simulation";
        Instant startDate = Instant.now().plus(10, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.LIVE);
        assertThat(simulation.isPaused()).isFalse();
        assertThat(simulation.getStartedAt()).isNotNull();
    }

    @Test
    void shouldRejectStartWhenNotPending() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // Future start for LIVE
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();  // First start succeeds

        assertThatThrownBy(simulation::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot start simulation")
            .hasMessageContaining("already started");
    }

    @Test
    void shouldTransitionFromSimulatingToLive() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.minus(90, ChronoUnit.DAYS);
        Instant endDate = now.minus(30, ChronoUnit.DAYS);  // BACKTEST mode (endDate in past)
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);

        simulation.transitionToLive();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.LIVE);
    }

    @Test
    void shouldCompleteSimulation() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant startDate = Instant.now().minus(60, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);  // End date in the past
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();

        simulation.complete();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.getProgress()).isEqualTo(100);
        assertThat(simulation.getCompletedAt()).isNotNull();
        assertThat(simulation.isTerminal()).isTrue();
    }

    @Test
    void shouldAllowCompletionRegardlessOfWallClockTime() {
        // complete() no longer validates wall-clock time — the executor validates
        // simulation time (lastExecutedTimestamp >= endDate) before calling complete().
        // Wall-clock checks were wrong for backtests where simulation time != wall clock.
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();
        simulation.complete();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.getCompletedAt()).isNotNull();
        assertThat(simulation.getProgress()).isEqualTo(100);
    }

    @Test
    void shouldFailSimulation() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // LIVE mode (future dates)
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();

        simulation.fail("test failure");

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.FAILED);
        assertThat(simulation.getCompletedAt()).isNotNull();
        assertThat(simulation.isTerminal()).isTrue();
    }

    @Test
    void shouldPauseSimulation() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // LIVE mode (future dates)
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();
        assertThat(simulation.isPaused()).isFalse();

        simulation.pause();

        assertThat(simulation.isPaused()).isTrue();
    }

    @Test
    void shouldResumeSimulation() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // LIVE mode (future dates)
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();
        simulation.pause();
        assertThat(simulation.isPaused()).isTrue();

        simulation.resume();

        assertThat(simulation.isPaused()).isFalse();
    }

    @Test
    void shouldUpdateProgress() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // Future dates for LIVE mode
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        simulation.updateProgress(50);

        assertThat(simulation.getProgress()).isEqualTo(50);
    }

    @Test
    void shouldClampProgressToValidRange() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant startDate = Instant.now().minus(60, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        simulation.updateProgress(150);
        assertThat(simulation.getProgress()).isEqualTo(100);

        simulation.updateProgress(-10);
        assertThat(simulation.getProgress()).isEqualTo(0);
    }

    @Test
    void shouldIdentifyRunningSimulation() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Test Simulation";
        Instant now = Instant.now();
        Instant startDate = now.plus(10, ChronoUnit.DAYS);  // LIVE mode (future dates)
        Instant endDate = now.plus(40, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);
        simulation.start();

        assertThat(simulation.isRunning()).isTrue();

        simulation.pause();
        assertThat(simulation.isRunning()).isFalse();
    }

    @Test
    void shouldIncludeBenchmarkIdInToString() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        BenchmarkId benchmarkId = BenchmarkId.generate();
        String name = "Test Simulation";
        Instant startDate = Instant.now().minus(60, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash, benchmarkId);

        String toString = simulation.toString();

        assertThat(toString).contains("benchmarkId=" + benchmarkId.toString());
        assertThat(toString).contains("createdAt=");
    }

    @Test
    void shouldCreateWithBrokerAccount() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money tradingLimit = new Money(new BigDecimal("25000"), Money.Currency.USD);

        Instant now = Instant.now();
        Simulation sim = new Simulation(
            SimulationId.generate(),
            "Test Sim",
            StrategyId.generate(),
            now.minus(30, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), // initialCash for now
            null, // benchmarkId
            accountId,
            tradingLimit
        );

        assertThat(sim.getBrokerAccountId()).isEqualTo(accountId);
        assertThat(sim.getTradingLimit()).isEqualTo(tradingLimit);
    }

    @Test
    void shouldCreateWithNullBrokerAccountForBackwardCompatibility() {
        SimulationId simId = SimulationId.generate();
        StrategyId strategyId = StrategyId.generate();
        String name = "Legacy Simulation";
        Instant now = Instant.now();
        Instant startDate = now.minus(30, ChronoUnit.DAYS);
        Instant endDate = now.minus(1, ChronoUnit.DAYS);
        Money initialCash = new Money(new BigDecimal("100000"), Money.Currency.USD);

        // Use existing constructor without broker account
        Simulation simulation = new Simulation(simId, name, strategyId, startDate, endDate, initialCash);

        // Broker account and trading limit should be null for backward compatibility
        assertThat(simulation.getBrokerAccountId()).isNull();
        assertThat(simulation.getTradingLimit()).isNull();
    }

    @Test
    void shouldForceCompleteALiveSimulation() {
        Simulation simulation = createLiveSimulation();
        simulation.start();
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.LIVE);

        simulation.forceComplete();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.isPaused()).isFalse();
        assertThat(simulation.getCompletedAt()).isNotNull();
        assertThat(simulation.isTerminal()).isTrue();
    }

    @Test
    void forceCompleteShouldNotClaimTheWholePeriodWasSimulated() {
        // Force-ending is how a user stops a run early. Stamping progress 100 on a simulation
        // that covered a third of its period claims the whole window was simulated, and every
        // performance figure read off it is then taken to cover that window.
        Simulation simulation = createBacktestSimulation();
        simulation.start();
        simulation.updateProgress(34);

        simulation.forceComplete();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.getProgress())
            .as("a force-ended simulation keeps the progress it actually reached")
            .isEqualTo(34);
    }

    @Test
    void shouldForceCompleteASimulatingSimulation() {
        Simulation simulation = createBacktestSimulation();
        simulation.start();
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);

        simulation.forceComplete();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.isTerminal()).isTrue();
    }

    @Test
    void shouldForceCompleteAPendingSimulation() {
        Simulation simulation = createLiveSimulation();
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);

        simulation.forceComplete();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
    }

    @Test
    void shouldRejectForceCompleteWhenAlreadyCompleted() {
        Simulation simulation = createBacktestSimulation();
        simulation.start();
        simulation.complete();

        assertThatThrownBy(simulation::forceComplete)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("terminal state");
    }

    @Test
    void shouldRejectForceCompleteWhenAlreadyFailed() {
        Simulation simulation = createLiveSimulation();
        simulation.start();
        simulation.fail("test failure");

        assertThatThrownBy(simulation::forceComplete)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("terminal state");
    }

    // Helper methods

    private Simulation createLiveSimulation() {
        Instant now = Instant.now();
        return new Simulation(
            SimulationId.generate(), "Live Sim", StrategyId.generate(),
            now.plus(10, ChronoUnit.DAYS), now.plus(40, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD)
        );
    }

    private Simulation createBacktestSimulation() {
        Instant now = Instant.now();
        return new Simulation(
            SimulationId.generate(), "Backtest Sim", StrategyId.generate(),
            now.minus(60, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD)
        );
    }

    @Test
    void shouldIncludeBrokerAccountInToString() {
        BrokerAccountId accountId = BrokerAccountId.generate();
        Money tradingLimit = new Money(new BigDecimal("25000"), Money.Currency.USD);

        Instant now = Instant.now();
        Simulation simulation = new Simulation(
            SimulationId.generate(),
            "Test Sim",
            StrategyId.generate(),
            now.minus(30, ChronoUnit.DAYS),
            now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD),
            null,
            accountId,
            tradingLimit
        );

        String toString = simulation.toString();

        assertThat(toString).contains("brokerAccountId=" + accountId.toString());
        assertThat(toString).contains("tradingLimit=" + tradingLimit.toString());
    }

    // ========== unassignFromBenchmark tests ==========

    @Test
    void shouldUnassignFromBenchmark() {
        BenchmarkId benchmarkId = BenchmarkId.generate();
        Instant now = Instant.now();
        Simulation simulation = new Simulation(
            SimulationId.generate(), "Test Sim", StrategyId.generate(),
            now.plus(10, ChronoUnit.DAYS), now.plus(40, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), benchmarkId);

        assertThat(simulation.getBenchmarkId()).isEqualTo(benchmarkId);

        simulation.unassignFromBenchmark();

        assertThat(simulation.getBenchmarkId()).isNull();
    }

    @Test
    void shouldThrowWhenUnassigningFromNoBenchmark() {
        Instant now = Instant.now();
        Simulation simulation = new Simulation(
            SimulationId.generate(), "Test Sim", StrategyId.generate(),
            now.plus(10, ChronoUnit.DAYS), now.plus(40, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD));

        assertThatThrownBy(simulation::unassignFromBenchmark)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not assigned to any benchmark");
    }

    @Test
    void shouldAllowReassignmentAfterUnassign() {
        BenchmarkId benchmarkId1 = BenchmarkId.generate();
        BenchmarkId benchmarkId2 = BenchmarkId.generate();
        Instant now = Instant.now();
        Simulation simulation = new Simulation(
            SimulationId.generate(), "Test Sim", StrategyId.generate(),
            now.plus(10, ChronoUnit.DAYS), now.plus(40, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), benchmarkId1);

        simulation.unassignFromBenchmark();
        simulation.assignToBenchmark(benchmarkId2);

        assertThat(simulation.getBenchmarkId()).isEqualTo(benchmarkId2);
    }

    // ========== restartFrom tests ==========

    @Test
    void shouldRestartFromCompletedState() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);
        simulation.setStatus(SimulationStatus.COMPLETED);
        simulation.setCompletedAt(now);
        simulation.setProgress(100);
        simulation.setPaused(false);

        simulation.restartFrom(50);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(simulation.isPaused()).isTrue();
        assertThat(simulation.getCompletedAt()).isNull();
        assertThat(simulation.getProgress()).isEqualTo(50);
    }

    @Test
    void shouldRestartFromFailedState() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);
        simulation.setStatus(SimulationStatus.FAILED);
        simulation.setCompletedAt(now);

        simulation.restartFrom(30);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(simulation.isPaused()).isTrue();
        assertThat(simulation.getProgress()).isEqualTo(30);
    }

    @Test
    void shouldRestartFromPausedSimulatingState() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);
        simulation.setStatus(SimulationStatus.SIMULATING);
        simulation.setPaused(true);
        simulation.setProgress(80);

        simulation.restartFrom(40);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.SIMULATING);
        assertThat(simulation.isPaused()).isTrue();
        assertThat(simulation.getProgress()).isEqualTo(40);
    }

    @Test
    void shouldThrowWhenRestartingRunningSimulation() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);
        simulation.setStatus(SimulationStatus.SIMULATING);
        simulation.setPaused(false);

        assertThatThrownBy(() -> simulation.restartFrom(50))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pause it first");
    }

    @Test
    void shouldThrowWhenRestartingFromPendingState() {
        Instant now = Instant.now();
        Simulation simulation = new Simulation(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(30, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD));

        assertThatThrownBy(() -> simulation.restartFrom(50))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("PENDING");
    }

    // ========== reconstitution tests ==========

    @Test
    void shouldReconstituteForceCompletedLiveSimulationWithNullEndDate() {
        // A force-completed LIVE simulation: status COMPLETED with endDate == null.
        // Reconstitution restores validated state and must NOT re-run transition rules.
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Force-completed LIVE")
            .strategyId(StrategyId.generate())
            .startDate(now.minus(5, ChronoUnit.DAYS))
            .endDate(null)
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.COMPLETED)
            .progress(100)
            .paused(false)
            .startedAt(now.minus(5, ChronoUnit.DAYS))
            .completedAt(now.minus(1, ChronoUnit.HOURS))
            .createdAt(now.minus(6, ChronoUnit.DAYS))
            .build();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.getEndDate()).isNull();
        assertThat(simulation.getProgress()).isEqualTo(100);
        assertThat(simulation.isPaused()).isFalse();
        assertThat(simulation.getCompletedAt()).isEqualTo(now.minus(1, ChronoUnit.HOURS));
        assertThat(simulation.getCreatedAt()).isEqualTo(now.minus(6, ChronoUnit.DAYS));
    }

    @Test
    void shouldReconstituteForceCompletedSimulationWithFutureEndDate() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Force-completed before end date")
            .strategyId(StrategyId.generate())
            .startDate(now.minus(5, ChronoUnit.DAYS))
            .endDate(now.plus(30, ChronoUnit.DAYS))
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.COMPLETED)
            .progress(100)
            .completedAt(now)
            .build();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
        assertThat(simulation.getCompletedAt()).isEqualTo(now);
    }

    @Test
    void shouldReconstituteFullPersistedState() {
        Instant now = Instant.now();
        BenchmarkId benchmarkId = BenchmarkId.generate();
        com.quorumtrading.model.valueobject.ChallengeId challengeId =
            com.quorumtrading.model.valueobject.ChallengeId.generate();
        com.quorumtrading.model.valueobject.UserId ownerUserId =
            com.quorumtrading.model.valueobject.UserId.generate();
        java.util.UUID backfillJobId = java.util.UUID.randomUUID();

        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Full state")
            .strategyId(StrategyId.generate())
            .startDate(now.minus(10, ChronoUnit.DAYS))
            .endDate(now.minus(1, ChronoUnit.DAYS))
            .initialCash(new Money(new BigDecimal("50000"), Money.Currency.USD))
            .benchmarkId(benchmarkId)
            .challengeId(challengeId)
            .status(SimulationStatus.LIVE)
            .progress(42)
            .paused(true)
            .startedAt(now.minus(9, ChronoUnit.DAYS))
            .createdAt(now.minus(11, ChronoUnit.DAYS))
            .version(7L)
            .lockedBy("instance-1")
            .lockedAt(now.minus(1, ChronoUnit.HOURS))
            .externalPause(true, "BUDGET_DEPLETED")
            .backfillJobId(backfillJobId)
            .skippedEvaluationCount(2)
            .totalSkippedEvaluationCount(5)
            .totalEvaluationCount(20)
            .archivedLLMCostUsd(new BigDecimal("1.25"))
            .archivedLLMPromptTokens(100)
            .archivedLLMCompletionTokens(50)
            .archivedLLMTotalTokens(150)
            .archivedLLMDecisionCount(3)
            .ownerUserId(ownerUserId)
            .notifyDecisions(true)
            .build();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.LIVE);
        assertThat(simulation.getProgress()).isEqualTo(42);
        assertThat(simulation.isPaused()).isTrue();
        assertThat(simulation.getBenchmarkId()).isEqualTo(benchmarkId);
        assertThat(simulation.getChallengeId()).isEqualTo(challengeId);
        assertThat(simulation.getStartedAt()).isEqualTo(now.minus(9, ChronoUnit.DAYS));
        assertThat(simulation.getCreatedAt()).isEqualTo(now.minus(11, ChronoUnit.DAYS));
        assertThat(simulation.getVersion()).isEqualTo(7L);
        assertThat(simulation.getLockedBy()).isEqualTo("instance-1");
        assertThat(simulation.getLockedAt()).isEqualTo(now.minus(1, ChronoUnit.HOURS));
        assertThat(simulation.isPausedForExternalReason()).isTrue();
        assertThat(simulation.getExternalPauseReason()).isEqualTo("BUDGET_DEPLETED");
        assertThat(simulation.getBackfillJobId()).isEqualTo(backfillJobId);
        assertThat(simulation.getSkippedEvaluationCount()).isEqualTo(2);
        assertThat(simulation.getTotalSkippedEvaluationCount()).isEqualTo(5);
        assertThat(simulation.getTotalEvaluationCount()).isEqualTo(20);
        assertThat(simulation.getArchivedLLMCostUsd()).isEqualByComparingTo("1.25");
        assertThat(simulation.getArchivedLLMPromptTokens()).isEqualTo(100);
        assertThat(simulation.getArchivedLLMCompletionTokens()).isEqualTo(50);
        assertThat(simulation.getArchivedLLMTotalTokens()).isEqualTo(150);
        assertThat(simulation.getArchivedLLMDecisionCount()).isEqualTo(3);
        assertThat(simulation.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(simulation.isNotifyDecisions()).isTrue();
    }

    @Test
    void shouldApplyReconstitutionDefaultsForNullValues() {
        Instant before = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Defaults")
            .strategyId(StrategyId.generate())
            .startDate(Instant.now())
            .endDate(null)
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(null)
            .createdAt(null)
            .archivedLLMCostUsd(null)
            .build();

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);
        assertThat(simulation.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(simulation.getArchivedLLMCostUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(simulation.isPausedForExternalReason()).isFalse();
        assertThat(simulation.getExternalPauseReason()).isNull();
    }

    @Test
    void shouldKeepLegacyReconstituteDefaults() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Legacy", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.PENDING);
        assertThat(simulation.getProgress()).isEqualTo(0);
        assertThat(simulation.isPaused()).isTrue();
        assertThat(simulation.getCreatedAt()).isNotNull();
    }

    // ========== setStatus hardening tests ==========

    @Test
    void shouldAllowSetStatusCompletedWhenEndDateIsNull() {
        // LIVE simulation without a scheduled end date — completing it must not NPE
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("LIVE no end date")
            .strategyId(StrategyId.generate())
            .startDate(now)
            .endDate(null)
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.LIVE)
            .build();

        simulation.setStatus(SimulationStatus.COMPLETED);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
    }

    @Test
    void shouldRejectSetStatusCompletedBeforeEndDate() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Future end date")
            .strategyId(StrategyId.generate())
            .startDate(now)
            .endDate(now.plus(30, ChronoUnit.DAYS))
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.LIVE)
            .build();

        assertThatThrownBy(() -> simulation.setStatus(SimulationStatus.COMPLETED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("before end date");
    }

    // ========== adoptTerminalStatus tests ==========

    @Test
    void shouldAdoptTerminalStatusWithoutValidation() {
        // In-memory running LIVE simulation with a future end date; a fresh copy was
        // force-completed externally — adopting the persisted status must not throw.
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Running LIVE")
            .strategyId(StrategyId.generate())
            .startDate(now)
            .endDate(now.plus(30, ChronoUnit.DAYS))
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.LIVE)
            .build();

        simulation.adoptTerminalStatus(SimulationStatus.COMPLETED);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.COMPLETED);
    }

    @Test
    void shouldAdoptFailedTerminalStatus() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Running LIVE")
            .strategyId(StrategyId.generate())
            .startDate(now)
            .endDate(null)
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.LIVE)
            .build();

        simulation.adoptTerminalStatus(SimulationStatus.FAILED);

        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.FAILED);
    }

    @Test
    void shouldRejectAdoptingNonTerminalStatus() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitution()
            .simulationId(SimulationId.generate())
            .name("Running LIVE")
            .strategyId(StrategyId.generate())
            .startDate(now)
            .endDate(null)
            .initialCash(new Money(new BigDecimal("100000"), Money.Currency.USD))
            .status(SimulationStatus.LIVE)
            .build();

        assertThatThrownBy(() -> simulation.adoptTerminalStatus(SimulationStatus.SIMULATING))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-terminal");
    }

    // ========== archiveLLMCost tests ==========

    @Test
    void shouldArchiveLLMCost() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);

        simulation.archiveLLMCost(new BigDecimal("0.50"), 1000, 500, 1500, 10);

        assertThat(simulation.getArchivedLLMCostUsd()).isEqualByComparingTo("0.50");
        assertThat(simulation.getArchivedLLMPromptTokens()).isEqualTo(1000);
        assertThat(simulation.getArchivedLLMCompletionTokens()).isEqualTo(500);
        assertThat(simulation.getArchivedLLMTotalTokens()).isEqualTo(1500);
        assertThat(simulation.getArchivedLLMDecisionCount()).isEqualTo(10);
    }

    @Test
    void shouldAccumulateArchivedLLMCost() {
        Instant now = Instant.now();
        Simulation simulation = Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);

        simulation.archiveLLMCost(new BigDecimal("0.50"), 1000, 500, 1500, 10);
        simulation.archiveLLMCost(new BigDecimal("0.30"), 600, 300, 900, 5);

        assertThat(simulation.getArchivedLLMCostUsd()).isEqualByComparingTo("0.80");
        assertThat(simulation.getArchivedLLMPromptTokens()).isEqualTo(1600);
        assertThat(simulation.getArchivedLLMCompletionTokens()).isEqualTo(800);
        assertThat(simulation.getArchivedLLMTotalTokens()).isEqualTo(2400);
        assertThat(simulation.getArchivedLLMDecisionCount()).isEqualTo(15);
    }

    // =====================================================================
    // Failure reason
    //
    // A FAILED simulation used to carry no cause at all: every API answered with status FAILED
    // and nothing else, so "no price data for that range", "the LLM provider is down" and "the
    // broker rejected the order" were indistinguishable — and they have nothing in common as
    // fixes. The reason lived only in the container log, which an API or MCP client cannot read.
    // =====================================================================

    private Simulation runnableSimulation() {
        Instant now = Instant.now();
        return Simulation.reconstitute(
            SimulationId.generate(), "Test", StrategyId.generate(),
            now.minus(90, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
            new Money(new BigDecimal("100000"), Money.Currency.USD), null, null, null);
    }

    @Test
    void shouldRecordWhyItFailed() {
        Simulation simulation = runnableSimulation();

        simulation.fail("Market-data backfill failed: no provider returned data for this range.");

        assertThat(simulation.getFailureReason())
            .isEqualTo("Market-data backfill failed: no provider returned data for this range.");
    }

    @Test
    void shouldHaveNoFailureReasonBeforeFailing() {
        assertThat(runnableSimulation().getFailureReason()).isNull();
    }

    @Test
    void shouldTreatABlankReasonAsNoReason() {
        Simulation simulation = runnableSimulation();

        simulation.fail("   ");

        assertThat(simulation.getFailureReason()).isNull();
    }

    @Test
    void shouldAcceptANullReasonWhenTheCauseIsGenuinelyUnknown() {
        Simulation simulation = runnableSimulation();

        simulation.fail(null);

        assertThat(simulation.getFailureReason()).isNull();
        assertThat(simulation.getStatus()).isEqualTo(SimulationStatus.FAILED);
    }

    @Test
    void shouldTruncateAnOverlongReasonToFitTheColumn() {
        Simulation simulation = runnableSimulation();

        simulation.fail("x".repeat(Simulation.MAX_FAILURE_REASON_LENGTH + 500));

        assertThat(simulation.getFailureReason())
            .hasSize(Simulation.MAX_FAILURE_REASON_LENGTH)
            .endsWith("\u2026");
    }

    @Test
    void shouldStripSurroundingWhitespaceFromTheReason() {
        Simulation simulation = runnableSimulation();

        simulation.fail("  broker rejected the order  ");

        assertThat(simulation.getFailureReason()).isEqualTo("broker rejected the order");
    }
}
