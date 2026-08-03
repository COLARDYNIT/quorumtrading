package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for broker account details.
 */
@Schema(description = "Broker account details response")
public record BrokerAccountResponse(

    @Schema(
        description = "Unique broker account identifier (UUID)",
        example = "660f9511-f39c-52e5-b827-557766551111"
    )
    String id,

    @Schema(
        description = "User ID who owns this broker account",
        example = "550e8400-e29b-41d4-a716-446655440000"
    )
    String userId,

    @Schema(
        description = "Display name for the broker account",
        example = "My Alpaca Paper"
    )
    String name,

    @Schema(
        description = "Type of broker (MOCK, ALPACA_PAPER, ALPACA_LIVE)",
        example = "ALPACA_PAPER"
    )
    String brokerType,

    @Schema(description = "Cash balance in the account")
    MoneyDto totalBalance,

    @Schema(description = "Total equity (cash + positions market value). Null for MOCK accounts.", nullable = true)
    MoneyDto equity,

    @Schema(description = "Balance reserved for active simulations")
    MoneyDto reservedBalance,

    @Schema(description = "Available balance for new simulations (total - reserved)")
    MoneyDto availableBalance,

    @Schema(
        description = "Account status (ACTIVE, INVALID_CREDENTIALS, DISABLED)",
        example = "ACTIVE"
    )
    String status,

    @Schema(
        description = "Mock broker configuration (only present for MOCK accounts)",
        nullable = true
    )
    MockBrokerConfigResponse mockConfig,

    @Schema(
        description = "Timestamp when the broker account was created",
        example = "2025-10-27T10:00:00Z"
    )
    Instant createdAt,

    @Schema(
        description = "Timestamp when the broker account was last updated",
        example = "2025-10-27T15:30:00Z"
    )
    Instant updatedAt

) {

    /**
     * DTO for monetary amounts.
     */
    @Schema(description = "Monetary amount with currency")
    public record MoneyDto(

        @Schema(
            description = "Amount value",
            example = "50000.00"
        )
        BigDecimal amount,

        @Schema(
            description = "Currency code (ISO 4217)",
            example = "USD"
        )
        String currency

    ) {}

    /**
     * Response DTO for mock broker configuration.
     */
    @Schema(description = "Mock broker configuration with simulated costs")
    public record MockBrokerConfigResponse(

        @Schema(
            description = "Initial capital configured for this mock broker",
            example = "100000.00"
        )
        BigDecimal initialCapital,

        @Schema(
            description = "Cost preset used (ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION, CUSTOM)",
            example = "TYPICAL_RETAIL"
        )
        String preset,

        @Schema(
            description = "Stock commission rate as a decimal",
            example = "0.0"
        )
        BigDecimal commissionRate,

        @Schema(
            description = "Crypto commission rate as a decimal",
            example = "0.0025"
        )
        BigDecimal cryptoCommissionRate,

        @Schema(
            description = "Base slippage rate as a decimal",
            example = "0.0005"
        )
        BigDecimal slippageBase,

        @Schema(
            description = "Market impact per unit traded",
            example = "0.00001"
        )
        BigDecimal slippageImpactPerUnit,

        @Schema(
            description = "Multiplier for volatility-adjusted slippage",
            example = "1.5"
        )
        BigDecimal slippageVolatilityMultiplier

    ) {}
}
