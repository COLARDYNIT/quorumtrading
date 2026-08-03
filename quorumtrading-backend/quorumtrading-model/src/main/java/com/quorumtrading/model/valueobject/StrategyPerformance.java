package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.util.List;

/**
 * Value object representing aggregate performance metrics for a trading strategy.
 * <p>
 * Contains statistics calculated from all completed simulations of a strategy:
 * - Total and completed simulation counts
 * - Average return percentage
 * - Win rate (percentage of profitable simulations)
 * - Average Sharpe ratio
 * - Best and worst returns
 * - Recent performance trend
 * </p>
 */
public record StrategyPerformance(
    int totalSimulations,
    int completedSimulations,
    BigDecimal avgReturn,
    BigDecimal winRate,
    BigDecimal avgSharpeRatio,
    BigDecimal bestReturn,
    BigDecimal worstReturn,
    List<BigDecimal> recentTrend,
    /**
     * How many of {@code completedSimulations} finished without making a single trading
     * decision — every evaluation skipped, or none ever fired.
     *
     * <p>Such a run has a portfolio value identical to its starting cash, so it contributes a
     * 0.00% return. Averaging that in reported an untested strategy as a losing one:
     * {@code avgReturn 0.00%, winRate 0.0000%, Sharpe 0} on the very view a user consults to
     * choose between strategies, where {@code winRate 0%} does not read as "unknown" — it reads
     * as "this loses". Empty runs are excluded from every metric and counted here instead, so
     * "flat" and "never actually ran" are distinguishable.</p>
     */
    int emptyRuns
) {

    /** For callers with no empty-run information; reports zero rather than guessing. */
    public StrategyPerformance(
            int totalSimulations,
            int completedSimulations,
            BigDecimal avgReturn,
            BigDecimal winRate,
            BigDecimal avgSharpeRatio,
            BigDecimal bestReturn,
            BigDecimal worstReturn,
            List<BigDecimal> recentTrend) {
        this(totalSimulations, completedSimulations, avgReturn, winRate, avgSharpeRatio,
            bestReturn, worstReturn, recentTrend, 0);
    }
    /**
     * Compact constructor with validation.
     */
    public StrategyPerformance {
        if (totalSimulations < 0) {
            throw new IllegalArgumentException("Total simulations cannot be negative");
        }
        if (completedSimulations < 0 || completedSimulations > totalSimulations) {
            throw new IllegalArgumentException("Completed simulations must be between 0 and total simulations");
        }
        // Every metric is deliberately nullable, and null means "not measurable" — never zero.
        //
        // The argument was already written here for avgSharpeRatio: a Sharpe ratio needs enough
        // daily snapshots to compute a standard deviation, so requiring non-null forced callers to
        // substitute ZERO, publishing a fabricated risk number that read exactly like a measured
        // risk-neutral one. That reasoning was never specific to Sharpe. A strategy whose every
        // completed run decided nothing has no average return and no win rate either, and reporting
        // 0.00% / 0% for them put "we have no measurement" and "measured, and it lost" behind the
        // same digits — on compare_strategies, which ranks on exactly these fields.
        //
        // Nullable does not mean unchecked: a caller with real returns must still pass them, which
        // the surrounding tests pin.
        if (emptyRuns < 0 || emptyRuns > completedSimulations) {
            throw new IllegalArgumentException("Empty runs must be between 0 and completed simulations");
        }
        if (recentTrend == null) {
            throw new IllegalArgumentException("Recent trend cannot be null");
        }
    }

    /**
     * A performance record for a strategy with no completed simulations.
     *
     * <p>Every metric is null, not zero: nothing has run, so nothing has been measured. Zeros here
     * would rank an untested strategy against measured ones as though it had lost.</p>
     */
    public static StrategyPerformance empty() {
        return new StrategyPerformance(0, 0, null, null, null, null, null, List.of());
    }

    /**
     * Check if this strategy has any completed simulations.
     */
    public boolean hasData() {
        return completedSimulations > 0;
    }

    /**
     * Check if the strategy has positive average returns.
     */
    public boolean isProfitable() {
        return avgReturn.compareTo(BigDecimal.ZERO) > 0;
    }
}
