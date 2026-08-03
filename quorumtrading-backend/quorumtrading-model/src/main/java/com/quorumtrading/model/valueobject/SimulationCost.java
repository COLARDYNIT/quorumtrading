package com.quorumtrading.model.valueobject;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Value object representing the aggregated AI cost of a simulation.
 * All cost figures are billed cost (raw cost * markup rate) — the user-facing price.
 *
 * @param totalCost           billed cost (raw * markup) summed across consensus decisions
 * @param totalPromptTokens   sum of prompt tokens
 * @param totalCompletionTokens sum of completion tokens
 * @param totalTokens         sum of total tokens
 * @param decisionCount       number of consensus rounds with cost data
 * @param partial             true if the simulation is not in a terminal state
 * @param progressPercent     0-100, from Simulation.getProgress()
 */
public record SimulationCost(
    BigDecimal totalCost,
    int totalPromptTokens,
    int totalCompletionTokens,
    int totalTokens,
    int decisionCount,
    boolean partial,
    int progressPercent
) {

    public SimulationCost {
        if (totalCost == null) {
            totalCost = BigDecimal.ZERO;
        }
        if (totalCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Total cost cannot be negative");
        }
        if (totalPromptTokens < 0) {
            throw new IllegalArgumentException("Total prompt tokens cannot be negative");
        }
        if (totalCompletionTokens < 0) {
            throw new IllegalArgumentException("Total completion tokens cannot be negative");
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("Total tokens cannot be negative");
        }
        if (decisionCount < 0) {
            throw new IllegalArgumentException("Decision count cannot be negative");
        }
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("Progress percent must be between 0 and 100");
        }
    }

    /**
     * Format the total billed cost as a dollar string, e.g. "$1.04".
     */
    public String formattedCost() {
        return "$" + totalCost.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Extrapolate the estimated total billed cost based on progress.
     * When the simulation is complete (not partial), returns the actual total cost.
     * When partial and progress > 0, extrapolates: totalCost / (progressPercent / 100.0).
     * When partial and progress is 0, returns the current cost (cannot extrapolate).
     */
    public BigDecimal estimatedTotalCost() {
        if (!partial) {
            return totalCost;
        }
        if (progressPercent == 0) {
            return totalCost;
        }
        return totalCost.multiply(new BigDecimal("100"))
            .divide(new BigDecimal(progressPercent), 6, RoundingMode.HALF_UP);
    }

    /**
     * Format the estimated total billed cost as a dollar string, e.g. "$2.08".
     */
    public String formattedEstimatedTotalCost() {
        return "$" + estimatedTotalCost().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
