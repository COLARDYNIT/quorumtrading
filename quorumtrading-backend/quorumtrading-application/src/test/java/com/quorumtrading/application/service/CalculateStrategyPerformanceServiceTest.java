package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.StrategyPerformance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for CalculateStrategyPerformanceService.
 *
 * <p><b>Test Coverage:</b></p>
 * <ul>
 *   <li>Return percentage calculation</li>
 *   <li>Win rate calculation</li>
 *   <li>Best/worst return tracking</li>
 *   <li>Recent trend extraction</li>
 *   <li>Edge cases (empty data, single simulation)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CalculateStrategyPerformanceServiceTest {

    @Mock
    private SimulationRepository simulationRepository;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private StrategyRepository strategyRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private SimulationContributionRepository contributionRepository;

    @Mock
    private com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository;

    private CalculateStrategyPerformanceService service;
    private StrategyId strategyId;

    @BeforeEach
    void setUp() {
        service = new CalculateStrategyPerformanceService(simulationRepository, portfolioRepository,
            strategyRepository, authorizationService, contributionRepository, consensusDecisionRepository);
        // Default: every completed run decided something. Tests that care override this.
        lenient().when(consensusDecisionRepository.findBySimulationId(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
            .thenReturn(java.util.List.of(mock(com.quorumtrading.model.entity.ConsensusDecision.class)));
        strategyId = new StrategyId(UUID.randomUUID());
        // The strategy is now loaded on every call, not only when a user was supplied — the
        // ownership check no longer sits behind an `if (requestingUserId != null)` guard.
        lenient().when(strategyRepository.findById(strategyId))
            .thenReturn(java.util.Optional.of(mock(com.quorumtrading.model.entity.Strategy.class)));
    }

    // ========== Helper Methods ==========

    @SuppressWarnings("deprecation")
    private Simulation createCompletedSimulation(Money initialCash, Instant completedAt) {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        // End date in the past to allow COMPLETED status
        Instant endDate = completedAt.minus(1, ChronoUnit.HOURS);
        Instant startDate = endDate.minus(30, ChronoUnit.DAYS);

        Simulation simulation = new Simulation(simId, "Test Sim", strategyId, startDate, endDate, initialCash);
        simulation.setStatus(SimulationStatus.COMPLETED);
        simulation.setCompletedAt(completedAt);
        return simulation;
    }

    @SuppressWarnings("deprecation")
    private Simulation createSimulatingSimulation(Money initialCash) {
        SimulationId simId = new SimulationId(UUID.randomUUID());
        Instant endDate = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant startDate = endDate.minus(30, ChronoUnit.DAYS);

        Simulation simulation = new Simulation(simId, "Test Sim", strategyId, startDate, endDate, initialCash);
        simulation.setStatus(SimulationStatus.SIMULATING);
        return simulation;
    }

    private PortfolioSnapshot createSnapshot(SimulationId simId, Money totalValue, Instant timestamp) {
        return new PortfolioSnapshot(
            simId,
            timestamp,
            Map.of(),
            totalValue,
            totalValue
        );
    }

    // ========== Return Calculation Tests ==========

    @Nested
    @DisplayName("Return Percentage Calculation")
    class ReturnCalculationTests {

        @Test
        @DisplayName("should calculate positive return correctly")
        void shouldCalculatePositiveReturn() {
            // Initial: $10,000, Final: $12,000 => 20% return
            Money initialCash = Money.usd(new BigDecimal("10000"));
            Money finalValue = Money.usd(new BigDecimal("12000"));

            Simulation sim = createCompletedSimulation(initialCash, Instant.now());
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
            when(portfolioRepository.findLatestBySimulationId(sim.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim.getSimulationId(), finalValue, Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("20.00"), result.avgReturn());
        }

        @Test
        @DisplayName("should calculate negative return correctly")
        void shouldCalculateNegativeReturn() {
            // Initial: $10,000, Final: $8,000 => -20% return
            Money initialCash = Money.usd(new BigDecimal("10000"));
            Money finalValue = Money.usd(new BigDecimal("8000"));

            Simulation sim = createCompletedSimulation(initialCash, Instant.now());
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
            when(portfolioRepository.findLatestBySimulationId(sim.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim.getSimulationId(), finalValue, Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("-20.00"), result.avgReturn());
        }

        @Test
        @DisplayName("should calculate zero return correctly")
        void shouldCalculateZeroReturn() {
            // Initial: $10,000, Final: $10,000 => 0% return
            Money initialCash = Money.usd(new BigDecimal("10000"));
            Money finalValue = Money.usd(new BigDecimal("10000"));

            Simulation sim = createCompletedSimulation(initialCash, Instant.now());
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
            when(portfolioRepository.findLatestBySimulationId(sim.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim.getSimulationId(), finalValue, Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("0.00"), result.avgReturn());
        }

        @Test
        @DisplayName("should calculate average return across multiple simulations")
        void shouldCalculateAverageReturnAcrossMultipleSimulations() {
            // Sim 1: 20% return, Sim 2: -10% return => Average: 5%
            Simulation sim1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation sim2 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1, sim2));

            when(portfolioRepository.findLatestBySimulationId(sim1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim1.getSimulationId(), Money.usd(new BigDecimal("12000")), Instant.now())));
            when(portfolioRepository.findLatestBySimulationId(sim2.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim2.getSimulationId(), Money.usd(new BigDecimal("9000")), Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            // (20 + (-10)) / 2 = 5%
            assertEquals(new BigDecimal("5.00"), result.avgReturn());
        }
    }

    // ========== Win Rate Tests ==========

    @Nested
    @DisplayName("Win Rate Calculation")
    class WinRateTests {

        @Test
        @DisplayName("should calculate 100% win rate when all simulations profitable")
        void shouldCalculate100PercentWinRate() {
            Simulation sim1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation sim2 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1, sim2));
            when(portfolioRepository.findLatestBySimulationId(sim1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim1.getSimulationId(), Money.usd(new BigDecimal("11000")), Instant.now())));
            when(portfolioRepository.findLatestBySimulationId(sim2.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim2.getSimulationId(), Money.usd(new BigDecimal("12000")), Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("100.0000"), result.winRate());
        }

        @Test
        @DisplayName("should calculate 0% win rate when all simulations lose money")
        void shouldCalculate0PercentWinRate() {
            Simulation sim1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation sim2 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1, sim2));
            when(portfolioRepository.findLatestBySimulationId(sim1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim1.getSimulationId(), Money.usd(new BigDecimal("9000")), Instant.now())));
            when(portfolioRepository.findLatestBySimulationId(sim2.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim2.getSimulationId(), Money.usd(new BigDecimal("8000")), Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("0.0000"), result.winRate());
        }

        @Test
        @DisplayName("should calculate 50% win rate with mixed results")
        void shouldCalculate50PercentWinRate() {
            Simulation sim1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation sim2 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1, sim2));
            when(portfolioRepository.findLatestBySimulationId(sim1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim1.getSimulationId(), Money.usd(new BigDecimal("12000")), Instant.now()))); // Win
            when(portfolioRepository.findLatestBySimulationId(sim2.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim2.getSimulationId(), Money.usd(new BigDecimal("8000")), Instant.now())));  // Loss
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("50.0000"), result.winRate());
        }

        @Test
        @DisplayName("should not count zero return as win")
        void shouldNotCountZeroReturnAsWin() {
            Simulation sim1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1));
            when(portfolioRepository.findLatestBySimulationId(sim1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim1.getSimulationId(), Money.usd(new BigDecimal("10000")), Instant.now()))); // 0% return
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("0.0000"), result.winRate());
        }
    }

    // ========== Best/Worst Return Tests ==========

    @Nested
    @DisplayName("Best and Worst Return Tracking")
    class BestWorstReturnTests {

        @Test
        @DisplayName("should track best and worst returns")
        void shouldTrackBestAndWorstReturns() {
            // Three simulations: +30%, +10%, -15%
            Simulation sim1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(2, ChronoUnit.DAYS));
            Simulation sim2 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation sim3 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim1, sim2, sim3));
            when(portfolioRepository.findLatestBySimulationId(sim1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim1.getSimulationId(), Money.usd(new BigDecimal("13000")), Instant.now()))); // +30%
            when(portfolioRepository.findLatestBySimulationId(sim2.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim2.getSimulationId(), Money.usd(new BigDecimal("11000")), Instant.now()))); // +10%
            when(portfolioRepository.findLatestBySimulationId(sim3.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(sim3.getSimulationId(), Money.usd(new BigDecimal("8500")), Instant.now())));  // -15%
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(new BigDecimal("30.0000"), result.bestReturn());
            assertEquals(new BigDecimal("-15.0000"), result.worstReturn());
        }
    }

    // ========== Recent Trend Tests ==========

    @Nested
    @DisplayName("Recent Trend Extraction")
    class RecentTrendTests {

        @Test
        @DisplayName("should extract last 4 returns for recent trend")
        void shouldExtractLast4ReturnsForRecentTrend() {
            // 5 simulations - should get most recent 4
            List<Simulation> simulations = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Simulation sim = createCompletedSimulation(
                    Money.usd(new BigDecimal("10000")),
                    Instant.now().minus(5 - i, ChronoUnit.DAYS) // Oldest first
                );
                simulations.add(sim);
            }

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(simulations);

            // Returns: 10%, 20%, 30%, 40%, 50% (oldest to newest)
            BigDecimal[] finalValues = {new BigDecimal("11000"), new BigDecimal("12000"),
                                    new BigDecimal("13000"), new BigDecimal("14000"), new BigDecimal("15000")};
            for (int i = 0; i < 5; i++) {
                when(portfolioRepository.findLatestBySimulationId(simulations.get(i).getSimulationId()))
                    .thenReturn(Optional.of(createSnapshot(simulations.get(i).getSimulationId(), Money.usd(finalValues[i]), Instant.now())));
            }
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            // Recent trend should be most recent 4 (sorted by completedAt DESC)
            assertEquals(4, result.recentTrend().size());
        }
    }

    // ========== Edge Cases ==========

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should return empty performance when no simulations exist")
        void shouldReturnEmptyPerformanceWhenNoSimulations() {
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertFalse(result.hasData());
            assertEquals(0, result.totalSimulations());
            assertEquals(0, result.completedSimulations());
        }

        @Test
        @DisplayName("should return empty performance when no completed simulations")
        void shouldReturnEmptyPerformanceWhenNoCompletedSimulations() {
            Simulation simulatingSim = createSimulatingSimulation(Money.usd(new BigDecimal("10000")));
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(simulatingSim));

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertFalse(result.hasData());
            assertEquals(1, result.totalSimulations());
            assertEquals(0, result.completedSimulations());
        }

        @Test
        @DisplayName("should handle simulation with missing portfolio snapshot")
        void shouldHandleSimulationWithMissingPortfolioSnapshot() {
            Simulation sim = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
            when(portfolioRepository.findLatestBySimulationId(any())).thenReturn(Optional.empty());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            // Should handle gracefully - returns empty
            assertFalse(result.hasData());
        }

        @Test
        @DisplayName("should handle simulation with zero initial cash")
        void shouldHandleSimulationWithZeroInitialCash() {
            // Service short-circuits when initial cash is zero BEFORE calling portfolioRepository
            Simulation sim = createCompletedSimulation(Money.usd(BigDecimal.ZERO), Instant.now());
            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
            // No portfolioRepository stub needed - service skips portfolio lookup when initial cash is zero

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            // Should handle gracefully - skip division by zero
            assertFalse(result.hasData());
        }
    }

    // ========== Simulation Count Tests ==========

    @Nested
    @DisplayName("Simulation Counts")
    class SimulationCountTests {

        @Test
        @DisplayName("should count total and completed simulations correctly")
        void shouldCountSimulationsCorrectly() {
            Simulation completed1 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation completed2 = createCompletedSimulation(Money.usd(new BigDecimal("10000")), Instant.now());
            Simulation simulating = createSimulatingSimulation(Money.usd(new BigDecimal("10000")));

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(completed1, completed2, simulating));
            when(portfolioRepository.findLatestBySimulationId(completed1.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(completed1.getSimulationId(), Money.usd(new BigDecimal("11000")), Instant.now())));
            when(portfolioRepository.findLatestBySimulationId(completed2.getSimulationId()))
                .thenReturn(Optional.of(createSnapshot(completed2.getSimulationId(), Money.usd(new BigDecimal("12000")), Instant.now())));
            when(portfolioRepository.findAllBySimulationId(any())).thenReturn(List.of());

            StrategyPerformance result = service.execute(new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(3, result.totalSimulations());
            assertEquals(2, result.completedSimulations());
        }
    }

    @Nested
    @DisplayName("Runs that decided nothing")
    class EmptyRuns {

        /** Stubs the decision lookup so this simulation reports having decided nothing. */
        private void hasNoDecisions(Simulation sim) {
            when(consensusDecisionRepository.findBySimulationId(
                    org.mockito.ArgumentMatchers.eq(sim.getSimulationId().value()),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        }

        @Test
        @DisplayName("an empty run is excluded from the metrics and counted separately")
        void emptyRunIsExcludedAndCounted() {
            // The defect: a run that skipped every evaluation ends with its portfolio value equal
            // to its starting cash, so it contributes an honest-looking 0.00%. Averaged in, three
            // such runs reported avgReturn 0.00% / winRate 0% / Sharpe 0 — and winRate 0% reads as
            // "this strategy loses", on the one view used to choose between strategies.
            Money cash = Money.usd(new BigDecimal("10000"));
            Simulation winner = createCompletedSimulation(cash, Instant.now().minus(1, ChronoUnit.DAYS));
            Simulation empty = createCompletedSimulation(cash, Instant.now().minus(2, ChronoUnit.DAYS));

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(winner, empty));
            when(portfolioRepository.findLatestBySimulationId(winner.getSimulationId()))
                .thenReturn(java.util.Optional.of(createSnapshot(
                    winner.getSimulationId(), Money.usd(new BigDecimal("11000")), Instant.now())));
            hasNoDecisions(empty);
            // No portfolio stub for the empty run on purpose: it is excluded before its value is
            // ever read, and Mockito's strict stubbing proves that rather than merely asserting it.

            StrategyPerformance performance = service.execute(
                new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(1, performance.emptyRuns());
            assertEquals(2, performance.completedSimulations());
            // 10% from the one real run, undiluted by the empty one's 0%.
            assertEquals(new BigDecimal("10.00"), performance.avgReturn());
            assertEquals(new BigDecimal("100.0000"), performance.winRate());
            assertEquals(1, performance.recentTrend().size());
        }

        @Test
        @DisplayName("an uncomputable Sharpe is reported absent, not as a measured zero")
        void uncomputableSharpeIsNull() {
            // A short backtest rarely has enough daily snapshots for a standard deviation.
            // Returning ZERO published a fabricated risk number that read exactly like a
            // genuinely measured risk-neutral result, on every short run.
            Money cash = Money.usd(new BigDecimal("10000"));
            Simulation sim = createCompletedSimulation(cash, Instant.now().minus(1, ChronoUnit.DAYS));

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(sim));
            when(portfolioRepository.findLatestBySimulationId(sim.getSimulationId()))
                .thenReturn(java.util.Optional.of(createSnapshot(
                    sim.getSimulationId(), Money.usd(new BigDecimal("11000")), Instant.now())));

            StrategyPerformance performance = service.execute(
                new CalculateStrategyPerformanceQuery(strategyId));

            assertNull(performance.avgSharpeRatio(),
                "with no daily snapshot series the ratio is unknown, not zero");
            assertEquals(new BigDecimal("10.00"), performance.avgReturn());
        }

        @Test
        @DisplayName("a strategy whose only runs decided nothing reports no metrics, not a loss")
        void allRunsEmptyReportsNoMetrics() {
            Money cash = Money.usd(new BigDecimal("10000"));
            Simulation empty = createCompletedSimulation(cash, Instant.now().minus(1, ChronoUnit.DAYS));

            when(simulationRepository.findByStrategyId(strategyId)).thenReturn(List.of(empty));
            hasNoDecisions(empty);

            StrategyPerformance performance = service.execute(
                new CalculateStrategyPerformanceQuery(strategyId));

            assertEquals(1, performance.emptyRuns());
            assertEquals(1, performance.completedSimulations());
            assertTrue(performance.recentTrend().isEmpty());
            // Was: winRate == ZERO. That is the defect, not the contract — on compare_strategies a
            // 0% win rate reads as "this loses", not as "nothing was measured". avgSharpeRatio was
            // already null for this exact condition; the other metrics now agree with it.
            assertNull(performance.winRate(), "no measurable run means no win rate");
            assertNull(performance.avgReturn());
            assertNull(performance.bestReturn());
            assertNull(performance.worstReturn());
            assertNull(performance.avgSharpeRatio());
        }
    }
}
