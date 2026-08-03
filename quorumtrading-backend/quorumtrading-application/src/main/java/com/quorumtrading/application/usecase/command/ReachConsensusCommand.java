package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;

/**
 * Command for triggering a consensus decision flow across all agents.
 *
 * This minimal command design pushes data collection responsibility to the service layer,
 * ensuring consistent data fetching logic regardless of entry point (backtesting, scheduler, or WebSocket).
 *
 * For each investment type + symbol combination (and cash), the service will:
 * 1. Fetch current portfolio, strategy, and simulation from repositories
 * 2. Fetch multi-timeframe market data (24h, 7d, 30d, 90d) up to analysisTimestamp
 * 3. Query each agent (Technical, Fundamental, Sentiment) for recommendations
 * 4. Store each LLMDecision and fire WebSocket event
 * 5. Query orchestrator with all agent decisions
 * 6. Store orchestrator LLMDecision and fire WebSocket event
 * 7. Use orchestrator's decision to determine final action (BUY/HOLD/SELL)
 *
 * @param simulationId        The simulation to analyze
 * @param analysisTimestamp   The point in time to analyze (for backtesting: simulation time, for real-time: Instant.now())
 */
public record ReachConsensusCommand(
    SimulationId simulationId,
    Instant analysisTimestamp
) {
}
