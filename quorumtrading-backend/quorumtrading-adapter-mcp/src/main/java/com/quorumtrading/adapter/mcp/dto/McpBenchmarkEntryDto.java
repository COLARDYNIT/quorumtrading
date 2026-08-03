package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * MCP DTO for a benchmark entry (a simulation's ranking within a benchmark).
 * Contains performance data for ranking strategies against each other.
 *
 * @param tradeCount number of executed BUY/SELL decisions. Zero means the strategy never
 *                   traded, so its return reflects nothing but the cash it started with —
 *                   without this field a do-nothing run reporting 0.00% is indistinguishable
 *                   from a strategy that traded to a flat result, and outranks every strategy
 *                   that took a real position in a down market.
 */
@RegisterForReflection
public record McpBenchmarkEntryDto(
    String simulationId,
    String simulationName,
    String strategyName,
    String initialCash,
    String tradingMode,
    String currentValue,
    String returnPercent,
    String status,
    long tradeCount
) {
}
