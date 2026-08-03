package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.BrokerAccountRepository;
import com.quorumtrading.application.port.out.ConsensusDecisionRepository;
import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.BuildBenchmarkLeaderboardUseCase;
import com.quorumtrading.application.usecase.dto.BenchmarkLeaderboard;
import com.quorumtrading.application.usecase.query.BuildBenchmarkLeaderboardQuery;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.BrokerType;
import com.quorumtrading.model.service.PerformanceCalculator;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Ranks the entries of a benchmark.
 *
 * <p>See {@link BenchmarkLeaderboard} for the ranking rule. The return baseline is the
 * <em>actual</em> initial portfolio value rather than the user-supplied seed: a broker-backed
 * simulation's first snapshot can be a bootstrap row carrying a placeholder cash figure that
 * does not match the real account balance, which would make every return in the benchmark
 * wrong by the ratio between the two. Backtests have no broker account and so always fall
 * straight through to their seed cash.</p>
 */
public class BuildBenchmarkLeaderboardService implements BuildBenchmarkLeaderboardUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(BuildBenchmarkLeaderboardService.class);

    /** How far into the snapshot history to look for the first row reflecting real broker state. */
    private static final int MAX_BOOTSTRAP_SNAPSHOTS_SCANNED = 10;

    private final BenchmarkRepository benchmarkRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final PortfolioRepository portfolioRepository;
    private final SimulationContributionRepository contributionRepository;
    private final ConsensusDecisionRepository consensusDecisionRepository;
    private final BrokerAccountRepository brokerAccountRepository;

    public BuildBenchmarkLeaderboardService(BenchmarkRepository benchmarkRepository,
                                            SimulationRepository simulationRepository,
                                            StrategyRepository strategyRepository,
                                            PortfolioRepository portfolioRepository,
                                            SimulationContributionRepository contributionRepository,
                                            ConsensusDecisionRepository consensusDecisionRepository,
                                            BrokerAccountRepository brokerAccountRepository) {
        this.benchmarkRepository = benchmarkRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.portfolioRepository = portfolioRepository;
        this.contributionRepository = contributionRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
        this.brokerAccountRepository = brokerAccountRepository;
    }

    @Override
    public BenchmarkLeaderboard execute(BuildBenchmarkLeaderboardQuery query) {
        benchmarkRepository.findById(query.benchmarkId())
            .orElseThrow(() -> new NoSuchElementException(
                "Benchmark not found: " + query.benchmarkId().value()));

        List<Simulation> members = simulationRepository.findByBenchmarkId(query.benchmarkId());

        Map<StrategyId, Optional<Strategy>> strategies = new HashMap<>();
        List<BenchmarkLeaderboard.Entry> entries = new ArrayList<>(members.size());
        for (int ordinal = 0; ordinal < members.size(); ordinal++) {
            Simulation sim = members.get(ordinal);
            String strategyName = strategies
                .computeIfAbsent(sim.getStrategyId(), strategyRepository::findById)
                .map(Strategy::getName)
                .orElse("Unknown Strategy");
            entries.add(buildEntry(sim, ordinal, strategyName));
        }

        entries.sort(Comparator
            .comparing(BenchmarkLeaderboard.Entry::traded).reversed()
            .thenComparing(Comparator.comparing(
                (BenchmarkLeaderboard.Entry e) -> e.returnPercent() != null
                    ? e.returnPercent() : BigDecimal.ZERO).reversed()));

        List<BenchmarkLeaderboard.Entry> ranked = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            ranked.add(entries.get(i).withRank(i + 1));
        }

        LOG.debug("Benchmark leaderboard {}: ranked {} entries", query.benchmarkId().value(), ranked.size());
        return new BenchmarkLeaderboard(ranked);
    }

    private BenchmarkLeaderboard.Entry buildEntry(Simulation sim, int ordinal, String strategyName) {
        Money seed = sim.getInitialCash();

        Optional<Money> measured = portfolioRepository.findLatestBySimulationId(sim.getSimulationId())
            .map(PortfolioSnapshot::getTotalValue);
        Money currentValue = measured.orElse(seed);
        boolean hasPortfolioData = measured.isPresent();

        Money baseline = findActualInitialValue(sim);

        BigDecimal returnPercent = null;
        if (baseline != null && currentValue != null) {
            List<Contribution> contributions =
                contributionRepository.findBySimulationId(sim.getSimulationId());
            returnPercent = PerformanceCalculator.simpleReturnPercent(baseline, contributions, currentValue);
        }

        long executedTrades = consensusDecisionRepository
            .countExecutedTradesBySimulationId(sim.getSimulationId().value());

        return new BenchmarkLeaderboard.Entry(
            0,
            ordinal,
            sim.getSimulationId(),
            sim.getName(),
            strategyName,
            deriveTradingMode(sim),
            seed,
            currentValue,
            hasPortfolioData,
            returnPercent,
            sim.getStatus(),
            executedTrades
        );
    }

    /**
     * The value the portfolio actually started from.
     *
     * <p>Backtests have no broker account, so their seed cash is the truth. For broker-backed
     * runs the first snapshot may be a bootstrap row written at start with the user-supplied
     * cash figure while the real account held something else; in that case the first snapshot
     * that carries positions reflects the real opening balance.</p>
     *
     * <p>A <b>mock</b> broker account counts as "no broker" here. Nothing outside the simulation
     * moves its balance, so the seed cash is exactly as true as it is for a broker-less backtest —
     * and taking the snapshot baseline instead made a benchmark entry disagree with
     * {@code get_simulation} about the same run's return (-5.94% against -5.99%) while shipping
     * the seed it had not used as that entry's own {@code initialCash}. Benchmarks exist to
     * produce that number; two answers for one run is the one thing they may not do.</p>
     */
    private Money findActualInitialValue(Simulation sim) {
        if (sim.getBrokerAccountId() == null || isMockBroker(sim)) {
            return sim.getInitialCash();
        }

        List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(sim.getSimulationId());
        if (snapshots.isEmpty()) {
            return sim.getInitialCash();
        }

        // findAllBySimulationId returns oldest first.
        PortfolioSnapshot oldest = snapshots.get(0);
        boolean isBootstrap = oldest.getPositions() == null || oldest.getPositions().isEmpty();

        if (isBootstrap && snapshots.size() > 1) {
            int scanLimit = Math.min(snapshots.size(), MAX_BOOTSTRAP_SNAPSHOTS_SCANNED);
            for (int i = 1; i < scanLimit; i++) {
                PortfolioSnapshot candidate = snapshots.get(i);
                if (candidate.getPositions() != null && !candidate.getPositions().isEmpty()) {
                    return candidate.getTotalValue();
                }
            }
        }

        return oldest.getTotalValue();
    }

    /** True when the run is backed by a mock broker, i.e. a backtest wearing a broker account. */
    private boolean isMockBroker(Simulation sim) {
        return brokerAccountRepository.findById(sim.getBrokerAccountId())
            .map(account -> account.getBrokerType() == BrokerType.MOCK)
            .orElse(false);
    }

    /** BACKTEST for mock or absent brokers, PAPER for simulated accounts, LIVE for real money. */
    private String deriveTradingMode(Simulation sim) {
        if (sim.getBrokerAccountId() == null) {
            return "BACKTEST";
        }
        return brokerAccountRepository.findById(sim.getBrokerAccountId())
            .map(account -> switch (account.getBrokerType()) {
                case MOCK -> "BACKTEST";
                case ALPACA_PAPER, SAXO_SIM -> "PAPER";
                case ALPACA_LIVE, SAXO_LIVE -> "LIVE";
            })
            .orElse("BACKTEST");
    }
}
