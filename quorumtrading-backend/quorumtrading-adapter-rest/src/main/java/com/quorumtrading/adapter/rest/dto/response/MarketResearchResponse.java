package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.MarketResearch;
import com.quorumtrading.model.valueobject.SymbolParser;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for market research configuration.
 */
@Schema(description = "Market research configuration")
public record MarketResearchResponse(
    @Schema(description = "Unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id,

    @Schema(description = "Market research name", example = "Bitcoin Weekly Opportunity Scanner")
    String name,

    @Schema(description = "Markdown-based research instructions")
    String description,

    @Schema(description = "List of ticker symbols being researched, in TICKER or TICKER:TYPE form. "
        + "STOCK assets are emitted bare; other types carry an explicit suffix (e.g. \"BTC:CRYPTO\") "
        + "so the value round-trips losslessly through create/update requests.",
        example = "[\"AAPL\", \"BTC:CRYPTO\"]")
    List<String> targetAssets,

    @Schema(description = "Research execution interval")
    IntervalResponse interval,

    @Schema(description = "Current status", example = "ACTIVE", enumeration = {"ACTIVE", "PAUSED", "COMPLETED"})
    String status,

    @Schema(description = "Auto-backtest enabled")
    boolean autoBacktest,

    @Schema(description = "Backtest configuration (null if autoBacktest is false)")
    BacktestConfigResponse backtestConfig,

    @Schema(description = "Creation timestamp")
    Instant createdAt,

    @Schema(description = "Last execution timestamp (null if never executed)")
    Instant lastExecutedAt,

    @Schema(description = "Next scheduled execution timestamp")
    Instant nextScheduledRun,

    @Schema(description = "Number of strategies created by this research")
    int createdStrategiesCount
) {
    /**
     * Nested DTO for research interval.
     */
    @Schema(description = "Research execution interval")
    public record IntervalResponse(
        @Schema(description = "Interval amount", example = "7")
        int amount,

        @Schema(description = "Interval unit", example = "DAYS", enumeration = {"DAYS", "WEEKS", "MONTHS"})
        String unit
    ) {}

    /**
     * Nested DTO for backtest configuration.
     */
    @Schema(description = "Backtest configuration")
    public record BacktestConfigResponse(
        @Schema(description = "Backtest duration (ISO-8601 duration)", example = "P30D")
        String backtestLength,

        @Schema(description = "Period start (ISO-8601 instant)", example = "2024-01-01T00:00:00Z")
        String periodStart,

        @Schema(description = "Period end (ISO-8601 instant)", example = "2024-01-31T23:59:59Z")
        String periodEnd
    ) {}

    /**
     * Convert domain MarketResearch entity to REST DTO without strategy count.
     * Used for create/update responses where count is not yet relevant.
     *
     * @param research the domain entity
     * @return the response DTO with createdStrategiesCount = 0
     */
    public static MarketResearchResponse fromDomain(MarketResearch research) {
        return fromDomain(research, 0);
    }

    /**
     * Convert domain MarketResearch entity to REST DTO with strategy count.
     *
     * @param research the domain entity
     * @param createdStrategiesCount the number of strategies created by this research
     * @return the response DTO
     */
    public static MarketResearchResponse fromDomain(MarketResearch research, int createdStrategiesCount) {
        // Emit the shared "TICKER[:TYPE]" convention (bare = STOCK) so a value read
        // from this response round-trips through create/update without silently
        // flipping a crypto asset to the STOCK default.
        List<String> assets = research.getTargetAssets().stream()
            .map(SymbolParser::formatWithTypeSuffix)
            .toList();

        IntervalResponse intervalResponse = new IntervalResponse(
            research.getInterval().amount(),
            research.getInterval().unit().name()
        );

        BacktestConfigResponse backtestConfigResponse = null;
        if (research.getBacktestConfig() != null) {
            var config = research.getBacktestConfig();
            backtestConfigResponse = new BacktestConfigResponse(
                config.backtestLength().toString(),
                config.periodStart() != null ? config.periodStart().toString() : null,
                config.periodEnd() != null ? config.periodEnd().toString() : null
            );
        }

        return new MarketResearchResponse(
            research.getId(),
            research.getName(),
            research.getMarkdownInstructions(),
            assets,
            intervalResponse,
            research.getStatus().name(),
            research.isEnableAutoBacktest(),
            backtestConfigResponse,
            research.getCreatedAt(),
            research.getLastExecutedAt(),
            research.getNextScheduledRun(),
            createdStrategiesCount
        );
    }
}
