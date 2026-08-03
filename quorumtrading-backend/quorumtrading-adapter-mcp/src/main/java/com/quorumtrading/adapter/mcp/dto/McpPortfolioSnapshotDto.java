package com.quorumtrading.adapter.mcp.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * MCP DTO for a portfolio snapshot at a point in time.
 */
@RegisterForReflection
public record McpPortfolioSnapshotDto(
    String simulationId,
    String timestamp,
    String totalValue,
    String cashAvailable,
    String currency,
    List<McpPositionDto> positions
) {
    @RegisterForReflection
    public record McpPositionDto(
        String ticker,
        String investmentType,
        String quantity,
        String averagePrice,
        String currentPrice,
        String marketValue,
        String unrealizedPnl
    ) {}
}
