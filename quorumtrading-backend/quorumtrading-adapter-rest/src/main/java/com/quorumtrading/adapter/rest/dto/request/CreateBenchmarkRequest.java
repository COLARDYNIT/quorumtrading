package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotEmpty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Request DTO for creating a new benchmark with multiple simulations.
 */
@Schema(description = "Request to create a benchmark for strategy comparison")
public record CreateBenchmarkRequest(
    @NotEmpty(message = "Strategy IDs are required and cannot be empty")
    @Schema(description = "List of strategy UUIDs to create simulations for",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\", \"660f9511-f39c-52e5-b827-557766551111\"]",
            required = true)
    List<String> strategyIds,

    @Schema(description = "Benchmark/simulation start date (ISO 8601 timestamp). " +
            "Required for backtest mode. For live mode, leave empty and system uses current time.",
            example = "2023-01-01T00:00:00Z",
            nullable = true)
    Instant startDate,

    @Schema(description = "Benchmark/simulation end date (ISO 8601 timestamp). " +
            "Null for live mode (ongoing competition), non-null for historical backtest.",
            example = "2023-12-31T23:59:59Z",
            nullable = true)
    Instant endDate,

    @Schema(description = "Optional custom name for the benchmark. " +
            "If not provided, a name will be auto-generated based on dates.",
            example = "Q1 2023 Strategy Battle",
            nullable = true)
    String name,

    @Schema(description = "Configuration for mock broker (for backtest or live mock trading). " +
            "Mutually exclusive with brokerAccountId.",
            nullable = true)
    MockBrokerConfigDto mockConfig,

    @Schema(description = "UUID of an existing broker account to use (for live paper/real trading). " +
            "Mutually exclusive with mockConfig. When provided, capitalPerStrategy is required.",
            example = "550e8400-e29b-41d4-a716-446655440000",
            nullable = true)
    String brokerAccountId,

    @Schema(description = "Amount of capital to reserve per simulation when using an existing broker account. " +
            "Required when brokerAccountId is provided.",
            example = "10000.00",
            nullable = true)
    BigDecimal capitalPerStrategy
) {
    @Schema(description = "Configuration for mock broker with simulated trading costs")
    public record MockBrokerConfigDto(
        @Schema(description = "Initial capital in USD for the mock account", example = "100000.00", nullable = true)
        BigDecimal initialCapital,
        @Schema(description = "Cost preset to use: ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION, CUSTOM", example = "TYPICAL_RETAIL", nullable = true)
        String preset
    ) {}
}
