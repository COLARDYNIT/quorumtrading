package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.StrategyPerformance;
import com.quorumtrading.model.valueobject.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A strategy whose every completed run decided nothing reported {@code avgReturn 0.00%} and
 * {@code winRate 0.0000%} — identical, by headline number, to one that genuinely traded and
 * finished flat.
 *
 * <p>{@code compare_strategies} ranks on exactly these fields, and on that view {@code winRate 0%}
 * does not read as "unknown"; it reads as "this loses". {@code avgSharpeRatio} already returned
 * null for the same condition, which is what made the other four look like a decision rather than
 * an oversight.</p>
 */
class StrategyPerformanceUnmeasurableTest {

    private static final UserId OWNER = new UserId(UUID.randomUUID());
    private static final StrategyId STRATEGY_ID = StrategyId.generate();

    private SimulationRepository simulationRepository;
    private ConsensusDecisionRepository consensusDecisionRepository;
    private CalculateStrategyPerformanceService service;

    @BeforeEach
    void setUp() {
        simulationRepository = mock(SimulationRepository.class);
        consensusDecisionRepository = mock(ConsensusDecisionRepository.class);
        PortfolioRepository portfolioRepository = mock(PortfolioRepository.class);
        StrategyRepository strategyRepository = mock(StrategyRepository.class);

        Strategy strategy = mock(Strategy.class);
        when(strategy.getOwnerId()).thenReturn(OWNER);
        when(strategyRepository.findById(STRATEGY_ID)).thenReturn(Optional.of(strategy));

        service = new CalculateStrategyPerformanceService(
            simulationRepository, portfolioRepository, strategyRepository,
            new AuthorizationService(), mock(SimulationContributionRepository.class),
            consensusDecisionRepository);
    }

    private Simulation completedRun() {
        Simulation sim = new Simulation(
            new com.quorumtrading.model.valueobject.SimulationId(UUID.randomUUID()),
            "empty run", STRATEGY_ID,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-31T00:00:00Z"),
            Money.usd(new BigDecimal("10000")));
        sim.start();
        sim.complete();
        return sim;
    }

    @Test
    @DisplayName("a strategy whose every run decided nothing reports null metrics, not zeros")
    void unmeasurableStrategyReportsNullsNotZeros() {
        Simulation empty = completedRun();
        when(simulationRepository.findByStrategyId(STRATEGY_ID)).thenReturn(List.of(empty));
        // No decisions at all — this is what makes the run unmeasurable.
        when(consensusDecisionRepository.findBySimulationId(
            any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        StrategyPerformance perf = service.execute(
            new CalculateStrategyPerformanceQuery(STRATEGY_ID, OWNER));

        assertThat(perf.completedSimulations()).isEqualTo(1);
        assertThat(perf.emptyRuns()).isEqualTo(1);

        assertThat(perf.avgReturn()).as("0.00% would read as a measured flat result").isNull();
        assertThat(perf.winRate()).as("0% would read as 'this loses', not 'unknown'").isNull();
        assertThat(perf.bestReturn()).isNull();
        assertThat(perf.worstReturn()).isNull();
        assertThat(perf.avgSharpeRatio()).isNull();
        assertThat(perf.recentTrend()).isEmpty();
    }
}
