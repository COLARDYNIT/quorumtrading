package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing an estimated cost for a backtest or benchmark.
 * Contains both the headline estimate and a confidence range (lower/upper bounds).
 *
 * @param tradingDays          Number of trading days in the period
 * @param totalConsensusRounds Total consensus rounds (tradingDays × evalsPerDay)
 * @param tickerCount          Number of tickers evaluated per round
 * @param agentsPerRound       Number of agents per round (enabled + orchestrator)
 * @param totalLLMCalls        Total LLM API calls expected
 * @param costPerCall          Estimated cost per LLM call (with markup)
 * @param estimatedCost        Best-guess total cost (with markup)
 * @param lowerBound           80% of estimatedCost (optimistic)
 * @param upperBound           120% of estimatedCost (conservative)
 * @param modelId              The model that will be used
 * @param modelName            Human-readable model name
 * @param dataCoverageWarning  Set when stored price data does not cover the priced window —
 *                             the estimate prices the schedule, and a fully-priced run over
 *                             an empty window still evaluates nothing. Null when coverage
 *                             was fine or was not measured.
 */
public record CostEstimate(
    int tradingDays,
    long totalConsensusRounds,
    int tickerCount,
    int agentsPerRound,
    long totalLLMCalls,
    BigDecimal costPerCall,
    BigDecimal estimatedCost,
    BigDecimal lowerBound,
    BigDecimal upperBound,
    String modelId,
    String modelName,
    String dataCoverageWarning
) {

    private static final BigDecimal LOWER_FACTOR = new BigDecimal("0.80");
    private static final BigDecimal UPPER_FACTOR = new BigDecimal("1.20");

    public CostEstimate {
        if (estimatedCost == null) estimatedCost = BigDecimal.ZERO;
        if (costPerCall == null) costPerCall = BigDecimal.ZERO;
        if (lowerBound == null) lowerBound = estimatedCost.multiply(LOWER_FACTOR).setScale(4, RoundingMode.HALF_UP);
        if (upperBound == null) upperBound = estimatedCost.multiply(UPPER_FACTOR).setScale(4, RoundingMode.HALF_UP);
    }

    /** Pre-coverage-warning form: everything else, no data-coverage measurement. */
    public CostEstimate(
        int tradingDays,
        long totalConsensusRounds,
        int tickerCount,
        int agentsPerRound,
        long totalLLMCalls,
        BigDecimal costPerCall,
        BigDecimal estimatedCost,
        BigDecimal lowerBound,
        BigDecimal upperBound,
        String modelId,
        String modelName
    ) {
        this(tradingDays, totalConsensusRounds, tickerCount, agentsPerRound, totalLLMCalls,
            costPerCall, estimatedCost, lowerBound, upperBound, modelId, modelName, null);
    }

    /** This estimate with a data-coverage warning attached (null clears it). */
    public CostEstimate withDataCoverageWarning(String warning) {
        return new CostEstimate(tradingDays, totalConsensusRounds, tickerCount, agentsPerRound,
            totalLLMCalls, costPerCall, estimatedCost, lowerBound, upperBound, modelId, modelName,
            warning);
    }

    /**
     * Create a CostEstimate computing bounds automatically from the estimated cost.
     */
    public static CostEstimate of(
        int tradingDays,
        long totalConsensusRounds,
        int tickerCount,
        int agentsPerRound,
        long totalLLMCalls,
        BigDecimal costPerCall,
        BigDecimal estimatedCost,
        String modelId,
        String modelName
    ) {
        BigDecimal lower = estimatedCost.multiply(LOWER_FACTOR).setScale(4, RoundingMode.HALF_UP);
        BigDecimal upper = estimatedCost.multiply(UPPER_FACTOR).setScale(4, RoundingMode.HALF_UP);
        return new CostEstimate(
            tradingDays, totalConsensusRounds, tickerCount, agentsPerRound,
            totalLLMCalls, costPerCall, estimatedCost, lower, upper, modelId, modelName
        );
    }

    /**
     * Format the estimated cost as a dollar amount (e.g., "$1.23").
     */
    public String formattedCost() {
        return "$" + estimatedCost.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Format the cost range (e.g., "$0.98 – $1.48").
     */
    public String formattedRange() {
        return "$" + lowerBound.setScale(2, RoundingMode.HALF_UP).toPlainString()
            + " – $" + upperBound.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
