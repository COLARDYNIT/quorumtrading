package com.quorumtrading.application.event;

import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event fired when a simulation completes execution.
 * Contains simulation results for downstream analysis.
 *
 * <p><b>Event-Driven Architecture:</b></p>
 * Part of the Phase 4 automation loop:
 * SimulationCompleted → ResultsAnalyzer → StrategyHealthChecker
 */
public record SimulationCompletedEvent(
    SimulationId simulationId,
    StrategyId strategyId,
    BigDecimal finalPortfolioValue,
    BigDecimal totalReturn,
    Instant completedAt
) {}
