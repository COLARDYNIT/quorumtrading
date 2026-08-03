package com.quorumtrading.adapter.rest.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * REST response DTO for strategy performance metrics.
 */
public record StrategyPerformanceResponse(
    int totalSimulations,
    int completedSimulations,
    BigDecimal avgReturn,
    BigDecimal winRate,
    /**
     * Annualised Sharpe ratio, or {@code null} when it could not be computed (too few daily
     * snapshots). Null means unknown, not zero.
     */
    BigDecimal avgSharpeRatio,
    BigDecimal bestReturn,
    BigDecimal worstReturn,
    List<BigDecimal> recentTrend,
    /**
     * How many of {@code completedSimulations} finished without making a single trading decision,
     * and were therefore excluded from every metric above.
     *
     * <p>Without it, a strategy whose only runs decided nothing reports zeros indistinguishable
     * from a strategy that was genuinely tested and went flat — and zeros on this screen read as
     * "this loses", not as "unknown".</p>
     */
    int emptyRuns
) {
}
