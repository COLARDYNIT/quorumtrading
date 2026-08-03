package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO for simulation details.
 */
@Schema(description = "Simulation details response")
public record SimulationResponse(
    @Schema(description = "Unique simulation identifier",
            example = "550e8400-e29b-41d4-a716-446655440000")
    String id,

    @Schema(description = "Strategy version ID",
            example = "660f9511-f39c-52e5-b827-557766551111")
    String strategyId,

    @Schema(description = "Simulation start date",
            example = "2023-01-01T00:00:00Z")
    Instant startDate,

    @Schema(description = "Simulation end date",
            example = "2023-12-31T23:59:59Z")
    Instant endDate,

    @Schema(description = "Current simulation status",
            example = "COMPLETED",
            enumeration = {"PENDING", "RUNNING", "COMPLETED", "FAILED"})
    String status,

    @Schema(description = "Simulation name",
            example = "BTC Momentum Strategy Backtest Q1 2023")
    String name,

    @Schema(description = "Completion progress (0-100)",
            example = "100")
    Integer progress,

    @Schema(description = "Whether the simulation execution is paused by user",
            example = "false")
    Boolean paused,

    @Schema(description = "Whether the simulation is paused by the system (e.g. budget depleted)",
            example = "false")
    Boolean pausedForExternalReason,

    @Schema(description = "Reason for system-initiated pause (e.g. BUDGET_DEPLETED)",
            example = "BUDGET_DEPLETED",
            nullable = true)
    String externalPauseReason,

    @Schema(description = "Timestamp when simulation was created",
            example = "2025-10-27T10:00:00Z")
    Instant createdAt,

    @Schema(description = "Timestamp when simulation started execution",
            example = "2025-10-27T10:01:00Z")
    Instant startedAt,

    @Schema(description = "Timestamp when simulation completed",
            example = "2025-10-27T10:15:00Z")
    Instant completedAt,

    @Schema(description = "Why the run failed. Null unless status is FAILED, and null for runs that "
                        + "failed before this field existed.",
            example = "Market-data backfill failed: no provider returned data for this range.")
    String failureReason,

    @Schema(description = "Benchmark ID if this simulation belongs to a benchmark",
            example = "036c8304-de23-49b5-bc38-b928a02bc404")
    String benchmarkId,

    @Schema(description = "Broker type used for trading (MOCK, ALPACA_PAPER, ALPACA_LIVE). Null for legacy simulations without broker account.",
            example = "MOCK",
            enumeration = {"MOCK", "ALPACA_PAPER", "ALPACA_LIVE"})
    String brokerType,

    @Schema(description = "Cost preset for mock broker (ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION, CUSTOM). Only present when brokerType is MOCK.",
            example = "ZERO_FEES",
            enumeration = {"ZERO_FEES", "TYPICAL_RETAIL", "HIGH_FRICTION", "CUSTOM"})
    String brokerPreset,

    @Schema(description = "Stock commission rate for mock broker (as decimal, e.g., 0.0 = 0%). Only present when brokerType is MOCK with CUSTOM preset.",
            example = "0.0")
    BigDecimal commissionRate,

    @Schema(description = "Crypto commission rate for mock broker (as decimal, e.g., 0.0025 = 0.25%). Only present when brokerType is MOCK with CUSTOM preset.",
            example = "0.0025")
    BigDecimal cryptoCommissionRate,

    @Schema(description = "Base slippage rate for mock broker. Only present when brokerType is MOCK with CUSTOM preset.",
            example = "0.0005")
    BigDecimal slippageBase,

    @Schema(description = "Linked backfill job ID when status is WAITING_FOR_DATA",
            example = "770e8400-e29b-41d4-a716-446655440001",
            nullable = true)
    String backfillJobId,

    @Schema(description = "Number of consecutive evaluation skips (for circuit breaker debugging)",
            example = "0")
    Integer skippedEvaluationCount,

    @Schema(description = "Total number of evaluations attempted",
            example = "10")
    Integer totalEvaluationCount,

    @Schema(description = "Initial cash balance the simulation started with",
            example = "100000.00")
    BigDecimal initialCash,

    @Schema(description = "Total cash invested = initialCash + Σ contributions. Null when not computed (list endpoints).",
            example = "1400.00",
            nullable = true)
    BigDecimal totalInvested,

    @Schema(description = "Time-weighted return as a percentage (industry-standard, ignores cashflow timing). " +
            "Null when not computed or insufficient data.",
            example = "12.34",
            nullable = true)
    BigDecimal twrPercent,

    @Schema(description = "Money-weighted return (IRR) as an annualized percentage. Null when not computed, " +
            "insufficient data, or IRR fails to converge.",
            example = "11.20",
            nullable = true)
    BigDecimal mwrPercent,

    @Schema(description = "Maximum drawdown as a negative percentage (-20.00 = a 20% peak-to-trough decline), " +
            "computed on the contribution-normalized equity curve so a DCA top-up is not mistaken for a draw. " +
            "Null when not computed or insufficient snapshots.",
            example = "-12.50",
            nullable = true)
    BigDecimal maxDrawdownPercent,

    @Schema(description = "All contributions injected into this simulation, oldest first. " +
            "Empty for backtests and for simulations with no recurring plan funding.",
            nullable = true)
    List<ContributionResponse> contributions,

    @Schema(description = "Provenance of the strategy attached to this simulation — whether the user " +
            "authored it themselves or forked it from a public strategy. Null when the strategy " +
            "no longer exists (deleted).",
            nullable = true)
    StrategyLineageResponse strategyLineage,

    @Schema(description = "Whether the owner has opted in to receive BUY/SELL decision notifications " +
            "for this simulation. HOLDs never trigger notifications regardless. Default false.",
            example = "false")
    Boolean notifyDecisions
) {}
