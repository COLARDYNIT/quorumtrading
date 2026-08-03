package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.PortfolioRepository;
import com.quorumtrading.application.port.out.SimulationContributionRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CalculateStrategyPerformanceUseCase;
import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.model.entity.Contribution;
import com.quorumtrading.model.entity.PortfolioSnapshot;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.service.PerformanceCalculator;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.StrategyPerformance;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Service for calculating aggregate performance metrics for a trading strategy.
 * <p>
 * Analyzes all completed simulations of a strategy to calculate:
 * - Average return percentage
 * - Win rate (percentage of profitable simulations)
 * - Best and worst returns
 * - Recent performance trend (last 4 simulations)
 * </p>
 */
public class CalculateStrategyPerformanceService implements CalculateStrategyPerformanceUseCase {

    private static final Logger LOG = Logger.getLogger(CalculateStrategyPerformanceService.class);
    private static final int RECENT_TREND_SIZE = 4;
    private static final int MIN_TRADING_DAYS_FOR_SHARPE = 30; // Minimum for statistical reliability
    private static final double ANNUALIZATION_FACTOR = Math.sqrt(252); // Trading days per year

    private final SimulationRepository simulationRepository;
    private final PortfolioRepository portfolioRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;
    private final SimulationContributionRepository contributionRepository;
    private final com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository;

    public CalculateStrategyPerformanceService(
            SimulationRepository simulationRepository,
            PortfolioRepository portfolioRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            SimulationContributionRepository contributionRepository,
            com.quorumtrading.application.port.out.ConsensusDecisionRepository consensusDecisionRepository) {
        this.simulationRepository = simulationRepository;
        this.portfolioRepository = portfolioRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
        this.contributionRepository = contributionRepository;
        this.consensusDecisionRepository = consensusDecisionRepository;
    }

    @Override
    public StrategyPerformance execute(CalculateStrategyPerformanceQuery query) {
        LOG.debugf("Calculating performance for strategy: %s", query.strategyId());

        // Authorization check
        Strategy strategy = strategyRepository.findById(query.strategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException("Strategy not found: " + query.strategyId()));
        authorizationService.requireView(query.requestingUserId(), strategy);

        // Step 1: Query all simulations for this strategy
        List<Simulation> allSimulations = simulationRepository.findByStrategyId(query.strategyId());

        // Filter to only completed simulations
        List<Simulation> completedSimulations = allSimulations.stream()
            .filter(sim -> sim.getStatus() == SimulationStatus.COMPLETED)
            .sorted(Comparator.comparing(Simulation::getCompletedAt).reversed())  // Most recent first
            .toList();

        // A run that decided nothing has a portfolio value identical to its starting cash, so it
        // contributes an honest-looking 0.00%. Averaging those in reported untested strategies as
        // losing ones on the one view used to choose between them. Excluded from every metric and
        // counted separately.
        List<Simulation> measurableSimulations = completedSimulations.stream()
            .filter(this::madeAtLeastOneDecision)
            .toList();
        int emptyRuns = completedSimulations.size() - measurableSimulations.size();

        LOG.debugf("Found %d total simulations, %d completed for strategy %s",
            allSimulations.size(), completedSimulations.size(), query.strategyId());

        // Step 2: If nothing measurable, report counts and nothing else.
        //
        // Every metric here is null, not zero. avgSharpeRatio already was — the rest reported a
        // hard 0, so a strategy whose every run decided nothing was indistinguishable by headline
        // number from one that genuinely traded and finished flat: both showed avgReturn 0.00% and
        // winRate 0.0000%. On compare_strategies, which ranks on exactly these fields, "we have no
        // measurement" was being rendered as "measured, and it lost".
        if (measurableSimulations.isEmpty()) {
            return new StrategyPerformance(
                allSimulations.size(),
                completedSimulations.size(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                emptyRuns
            );
        }

        // Step 3: Calculate returns for each completed simulation
        List<BigDecimal> returns = new ArrayList<>();

        for (Simulation simulation : measurableSimulations) {
            Optional<BigDecimal> returnPct = calculateSimulationReturn(simulation);
            returnPct.ifPresent(returns::add);
        }

        // Step 4: If no valid returns calculated, return empty performance
        if (returns.isEmpty()) {
            LOG.warnf("No valid returns calculated for strategy %s despite having completed simulations", query.strategyId());
            return StrategyPerformance.empty();
        }

        // Step 5: Calculate aggregate metrics
        BigDecimal avgReturn = calculateAverage(returns);
        BigDecimal winRate = calculateWinRate(returns);
        BigDecimal bestReturn = returns.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal worstReturn = returns.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        // Calculate proper annualized Sharpe ratio from daily returns within each simulation
        BigDecimal avgSharpeRatio = calculateAverageSharpeRatio(measurableSimulations);

        // Step 6: Get recent trend (last N returns)
        List<BigDecimal> recentTrend = returns.stream()
            .limit(RECENT_TREND_SIZE)
            .toList();

        LOG.infof("Strategy %s performance: avgReturn=%.2f%%, winRate=%.2f%%, sharpe=%.2f, completed=%d/%d",
            query.strategyId(), avgReturn, winRate, avgSharpeRatio,
            completedSimulations.size(), allSimulations.size());

        return new StrategyPerformance(
            allSimulations.size(),
            completedSimulations.size(),
            avgReturn,
            winRate,
            avgSharpeRatio,
            bestReturn,
            worstReturn,
            recentTrend,
            emptyRuns
        );
    }

    /**
     * Whether a finished run produced at least one real trading decision.
     *
     * <p>Asks for a single non-SKIP decision rather than counting them all — one is enough to
     * prove the run evaluated something, and this runs once per completed simulation.</p>
     */
    private boolean madeAtLeastOneDecision(Simulation simulation) {
        try {
            return !consensusDecisionRepository.findBySimulationId(
                simulation.getSimulationId().value(), null, null, null, null,
                List.of("BUY", "SELL", "HOLD"), 1).isEmpty();
        } catch (Exception e) {
            // Never let an advisory exclusion hide a strategy's real performance.
            LOG.debugf("Could not check decisions for simulation %s, counting it as measurable: %s",
                simulation.getSimulationId().value(), e.getMessage());
            return true;
        }
    }

    /**
     * Calculate return percentage for a single simulation.
     * return % = ((finalValue - totalInvested) / totalInvested) * 100, where
     * totalInvested = initialCash + Σ contributions. Using total invested as the
     * basis prevents DCA top-ups from being counted as profit; for simulations
     * with no contributions this equals initialCash and the result is unchanged.
     */
    private Optional<BigDecimal> calculateSimulationReturn(Simulation simulation) {
        try {
            // Get initial cash
            Money initialCash = simulation.getInitialCash();
            if (initialCash == null || initialCash.amount().compareTo(BigDecimal.ZERO) == 0) {
                LOG.warnf("Simulation %s has null or zero initial cash", simulation.getSimulationId());
                return Optional.empty();
            }

            // Get final portfolio value
            Optional<PortfolioSnapshot> latestSnapshot =
                portfolioRepository.findLatestBySimulationId(simulation.getSimulationId());

            if (latestSnapshot.isEmpty()) {
                LOG.warnf("No portfolio snapshot found for completed simulation %s", simulation.getSimulationId());
                return Optional.empty();
            }

            Money finalValue = latestSnapshot.get().getTotalValue();
            if (finalValue == null) {
                LOG.warnf("Final portfolio snapshot has null total value for simulation %s", simulation.getSimulationId());
                return Optional.empty();
            }

            // Basis = total invested (seed + contributions). Equals initialCash when no DCA.
            List<Contribution> contributions =
                contributionRepository.findBySimulationId(simulation.getSimulationId());
            BigDecimal invested = PerformanceCalculator.totalInvested(initialCash, contributions).amount();
            if (invested.compareTo(BigDecimal.ZERO) == 0) {
                return Optional.empty();
            }

            // Calculate return percentage
            BigDecimal returnAmount = finalValue.amount().subtract(invested);
            BigDecimal returnPct = returnAmount
                .divide(invested, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

            LOG.debugf("Simulation %s: invested=%s, final=%s, return=%.2f%%",
                simulation.getSimulationId(), invested, finalValue.amount(), returnPct);

            return Optional.of(returnPct);
        } catch (Exception e) {
            LOG.errorf(e, "Error calculating return for simulation %s", simulation.getSimulationId());
            return Optional.empty();
        }
    }

    /**
     * Calculate average of a list of BigDecimal values.
     */
    private BigDecimal calculateAverage(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate win rate (percentage of positive returns).
     */
    private BigDecimal calculateWinRate(List<BigDecimal> returns) {
        if (returns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long wins = returns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).count();
        return BigDecimal.valueOf(wins)
            .divide(BigDecimal.valueOf(returns.size()), 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
    }

    /**
     * Calculate average annualized Sharpe ratio across all completed simulations.
     * For each simulation, calculates Sharpe from daily portfolio returns, then averages.
     *
     * <p>Formula per simulation: Sharpe = (avgDailyReturn / stdDevDailyReturn) * sqrt(252)</p>
     *
     * @param completedSimulations list of completed simulations
     * @return average annualized Sharpe ratio, or 0 if insufficient data
     */
    private BigDecimal calculateAverageSharpeRatio(List<Simulation> completedSimulations) {
        List<BigDecimal> sharpeRatios = new ArrayList<>();

        for (Simulation simulation : completedSimulations) {
            Optional<BigDecimal> sharpe = calculateSimulationSharpeRatio(simulation);
            sharpe.ifPresent(sharpeRatios::add);
        }

        if (sharpeRatios.isEmpty()) {
            // Absent, not zero. A short backtest rarely has enough daily snapshots to compute a
            // Sharpe ratio at all, and returning ZERO published a fabricated risk number on every
            // one of them — indistinguishable from a genuinely measured risk-neutral result, and
            // the only record that it was unmeasurable was this debug line.
            LOG.debug("No valid Sharpe ratios calculated (insufficient daily data) — reporting absent");
            return null;
        }

        return calculateAverage(sharpeRatios);
    }

    /**
     * Calculate annualized Sharpe ratio for a single simulation using daily returns.
     *
     * <p>Process:
     * <ol>
     *   <li>Get all portfolio snapshots for the simulation</li>
     *   <li>Extract daily closing values (last snapshot per day)</li>
     *   <li>Calculate daily returns: (value[t] - value[t-1]) / value[t-1]</li>
     *   <li>Compute: Sharpe = (mean(dailyReturns) / stdDev(dailyReturns)) * sqrt(252)</li>
     * </ol>
     * </p>
     *
     * @param simulation the simulation to analyze
     * @return annualized Sharpe ratio, or empty if insufficient data
     */
    private Optional<BigDecimal> calculateSimulationSharpeRatio(Simulation simulation) {
        try {
            // Get all portfolio snapshots
            List<PortfolioSnapshot> snapshots = portfolioRepository.findAllBySimulationId(simulation.getSimulationId());

            if (snapshots.size() < 2) {
                LOG.debugf("Simulation %s has fewer than 2 snapshots, skipping Sharpe calculation",
                    simulation.getSimulationId());
                return Optional.empty();
            }

            // Extract daily closing values (last snapshot per day)
            Map<LocalDate, BigDecimal> dailyClosingValues = extractDailyClosingValues(snapshots);

            if (dailyClosingValues.size() < MIN_TRADING_DAYS_FOR_SHARPE) {
                LOG.debugf("Simulation %s has only %d trading days, need at least %d for Sharpe",
                    simulation.getSimulationId(), dailyClosingValues.size(), MIN_TRADING_DAYS_FOR_SHARPE);
                return Optional.empty();
            }

            // Calculate daily returns
            List<BigDecimal> dailyReturns = calculateDailyReturns(dailyClosingValues);

            if (dailyReturns.size() < MIN_TRADING_DAYS_FOR_SHARPE - 1) {
                return Optional.empty();
            }

            // Calculate mean and std dev of daily returns
            BigDecimal avgDailyReturn = calculateAverageDecimal(dailyReturns);
            BigDecimal stdDevDailyReturn = calculateStdDev(dailyReturns, avgDailyReturn);

            if (stdDevDailyReturn.compareTo(BigDecimal.ZERO) == 0) {
                LOG.debugf("Simulation %s has zero volatility, returning Sharpe=0", simulation.getSimulationId());
                return Optional.of(BigDecimal.ZERO);
            }

            // Calculate annualized Sharpe ratio
            // Sharpe = (avgDailyReturn / stdDevDailyReturn) * sqrt(252)
            BigDecimal dailySharpe = avgDailyReturn.divide(stdDevDailyReturn, 6, RoundingMode.HALF_UP);
            BigDecimal annualizedSharpe = dailySharpe.multiply(BigDecimal.valueOf(ANNUALIZATION_FACTOR));

            LOG.debugf("Simulation %s: %d trading days, avgDaily=%.6f%%, stdDev=%.6f%%, Sharpe=%.2f",
                simulation.getSimulationId(), dailyClosingValues.size(),
                avgDailyReturn.multiply(BigDecimal.valueOf(100)),
                stdDevDailyReturn.multiply(BigDecimal.valueOf(100)),
                annualizedSharpe);

            return Optional.of(annualizedSharpe.setScale(2, RoundingMode.HALF_UP));

        } catch (Exception e) {
            LOG.errorf(e, "Error calculating Sharpe ratio for simulation %s", simulation.getSimulationId());
            return Optional.empty();
        }
    }

    /**
     * Extract daily closing values from portfolio snapshots.
     * For each day, takes the last (most recent) snapshot's total value.
     */
    private Map<LocalDate, BigDecimal> extractDailyClosingValues(List<PortfolioSnapshot> snapshots) {
        TreeMap<LocalDate, BigDecimal> dailyValues = new TreeMap<>();

        // Sort by timestamp descending so we process later snapshots first
        snapshots.stream()
            .filter(s -> s.getTotalValue() != null && s.getTimestamp() != null)
            .sorted(Comparator.comparing(PortfolioSnapshot::getTimestamp).reversed())
            .forEach(snapshot -> {
                LocalDate day = snapshot.getTimestamp().atZone(ZoneOffset.UTC).toLocalDate();
                // putIfAbsent keeps the first (latest) value for each day
                dailyValues.putIfAbsent(day, snapshot.getTotalValue().amount());
            });

        return dailyValues;
    }

    /**
     * Calculate daily returns from ordered daily closing values.
     * Return = (value[t] - value[t-1]) / value[t-1]
     */
    private List<BigDecimal> calculateDailyReturns(Map<LocalDate, BigDecimal> dailyClosingValues) {
        List<BigDecimal> returns = new ArrayList<>();
        BigDecimal previousValue = null;

        for (BigDecimal value : dailyClosingValues.values()) {
            if (previousValue != null && previousValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = value.subtract(previousValue)
                    .divide(previousValue, 8, RoundingMode.HALF_UP);
                returns.add(dailyReturn);
            }
            previousValue = value;
        }

        return returns;
    }

    /**
     * Calculate average with higher precision for daily returns.
     */
    private BigDecimal calculateAverageDecimal(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 8, RoundingMode.HALF_UP);
    }

    /**
     * Calculate sample standard deviation.
     * Uses (n-1) denominator for sample std dev.
     */
    private BigDecimal calculateStdDev(List<BigDecimal> values, BigDecimal mean) {
        if (values.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumSquaredDiff = values.stream()
            .map(v -> v.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Use (n-1) for sample standard deviation
        BigDecimal variance = sumSquaredDiff.divide(
            BigDecimal.valueOf(values.size() - 1), 10, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
}
