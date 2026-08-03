package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * MCP DTO for strategy performance metrics.
 */
@RegisterForReflection
public record McpStrategyPerformanceDto(
    String strategyId,
    String strategyName,
    int totalSimulations,
    int completedSimulations,
    String avgReturnPercent,
    String winRatePercent,
    /**
     * Annualised Sharpe ratio, or {@code null} when it could not be computed — a short backtest
     * rarely has enough daily snapshots for a standard deviation. Null means unknown; it does not
     * mean zero, and reporting zero made an unmeasurable run look risk-neutral.
     */
    String avgSharpeRatio,
    String bestReturnPercent,
    String worstReturnPercent,
    List<String> recentReturnPercents,
    /**
     * How many of {@code completedSimulations} finished without making a single trading decision.
     *
     * <p>Those runs are excluded from every metric above, because a run that decided nothing ends
     * with its portfolio equal to its starting cash and so contributes an honest-looking 0.00% —
     * which averaged an untested strategy down into looking like a losing one. Without this count
     * the exclusion is invisible: a strategy whose only runs were empty is byte-identical to one
     * that was really tested and really went flat.</p>
     */
    int emptyRuns
) {}
