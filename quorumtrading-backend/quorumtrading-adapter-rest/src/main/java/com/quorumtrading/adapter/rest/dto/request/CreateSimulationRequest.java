package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

/**
 * Request DTO for creating a new simulation.
 */
@Schema(description = "Request to create a new backtesting simulation")
public record CreateSimulationRequest(
    @NotBlank(message = "Strategy ID is required")
    @Schema(description = "Strategy version ID to use for this simulation",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true)
    String strategyId,

    @Schema(description = "Simulation start date (ISO 8601 timestamp). Required for BACKTEST mode. " +
            "For LIVE mode, leave empty and the system will use the current time.",
            example = "2023-01-01T00:00:00Z")
    Instant startDate,

    @NotNull(message = "End date is required")
    @Schema(description = "Simulation end date (ISO 8601 timestamp)",
            example = "2023-12-31T23:59:59Z",
            required = true)
    Instant endDate,

    @Schema(description = "Optional simulation name",
            example = "BTC Momentum Strategy Backtest Q1 2023")
    String name,

    @Schema(description = "Initial cash balance in USD",
            example = "100000.00",
            defaultValue = "100000.00")
    Double initialCash,

    @Schema(description = "Optional broker account to trade through. Omit for a mock-broker "
            + "backtest. Supplying one ring-fences this simulation's capital against that "
            + "account, which is what makes paper and live trading possible. "
            + "⚠️ An ALPACA_LIVE or SAXO_LIVE account here means this simulation places orders "
            + "with REAL money — see confirmLive. The account must belong to you.",
            example = "550e8400-e29b-41d4-a716-446655440000")
    String brokerAccountId,

    @Schema(description = "Capital to ring-fence on the broker account for this simulation. "
            + "Defaults to initialCash when a broker account is supplied.",
            example = "10000.00")
    Double tradingLimit,

    @Schema(description = "Required true when brokerAccountId names a live account (ALPACA_LIVE, "
            + "SAXO_LIVE): acknowledges that this simulation trades REAL money. The request is "
            + "rejected without it. Ignored for backtests, mock and paper accounts.",
            example = "false", defaultValue = "false")
    boolean confirmLive
) {
    public CreateSimulationRequest {
        // Apply defaults
        if (initialCash == null) {
            initialCash = 100000.0;
        }
        // The trading limit is what gets reserved on the account; defaulting it to the
        // simulation's own cash is the only reading that cannot over-commit the account.
        if (brokerAccountId != null && !brokerAccountId.isBlank() && tradingLimit == null) {
            tradingLimit = initialCash;
        }
    }
}
