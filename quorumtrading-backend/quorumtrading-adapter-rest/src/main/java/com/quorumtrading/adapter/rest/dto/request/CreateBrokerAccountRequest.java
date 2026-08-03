package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new broker account.
 *
 * <p><b>Request Structure by Broker Type:</b></p>
 * <ul>
 *   <li><b>MOCK:</b> Requires mockConfig, credentials must be null</li>
 *   <li><b>ALPACA_PAPER/ALPACA_LIVE:</b> Requires credentials, mockConfig must be null</li>
 * </ul>
 */
@Schema(description = "Request to create a new broker account")
public record CreateBrokerAccountRequest(

    @NotBlank(message = "Account name is required")
    @Schema(
        description = "Display name for the broker account",
        example = "My Alpaca Paper",
        required = true
    )
    String name,

    @NotNull(message = "Broker type is required")
    @Schema(
        description = "Type of broker (MOCK, ALPACA_PAPER, ALPACA_LIVE)",
        example = "ALPACA_PAPER",
        required = true
    )
    String brokerType,

    @Schema(
        description = "API credentials for real brokers (required for ALPACA_PAPER, ALPACA_LIVE; must be null for MOCK)",
        nullable = true
    )
    BrokerCredentialsDto credentials,

    @Schema(
        description = "Configuration for mock broker (required for MOCK; must be null for real brokers)",
        nullable = true
    )
    MockBrokerConfigDto mockConfig

) {

    /**
     * DTO for broker API credentials.
     */
    @Schema(description = "API credentials for a real broker")
    public record BrokerCredentialsDto(

        @NotBlank(message = "API key is required for real brokers")
        @Schema(
            description = "The broker API key",
            example = "PK1234567890",
            required = true
        )
        String apiKey,

        @NotBlank(message = "API secret is required for real brokers")
        @Schema(
            description = "The broker API secret",
            example = "secret1234567890abcdef",
            required = true
        )
        String apiSecret

    ) {}

    /**
     * DTO for mock broker configuration.
     */
    @Schema(description = "Configuration for mock broker with simulated costs")
    public record MockBrokerConfigDto(

        @NotNull(message = "Initial capital is required for mock brokers")
        @Schema(
            description = "Initial capital in USD",
            example = "100000",
            required = true
        )
        BigDecimal initialCapital,

        @Schema(
            description = "Cost preset to use (ZERO_FEES, TYPICAL_RETAIL, HIGH_FRICTION). " +
                "If provided, individual cost parameters are ignored.",
            example = "TYPICAL_RETAIL",
            nullable = true
        )
        String preset,

        @Schema(
            description = "Stock commission rate as a decimal (e.g., 0.0 for 0%). " +
                "Required if preset is not provided or is CUSTOM.",
            example = "0.0",
            nullable = true
        )
        BigDecimal commissionRate,

        @Schema(
            description = "Crypto commission rate as a decimal (e.g., 0.0025 for 0.25%). " +
                "Required if preset is not provided or is CUSTOM.",
            example = "0.0025",
            nullable = true
        )
        BigDecimal cryptoCommissionRate,

        @Schema(
            description = "Base slippage rate as a decimal",
            example = "0.0005",
            nullable = true
        )
        BigDecimal slippageBase,

        @Schema(
            description = "Market impact per unit traded",
            example = "0.00001",
            nullable = true
        )
        BigDecimal slippageImpactPerUnit,

        @Schema(
            description = "Multiplier for volatility-adjusted slippage",
            example = "1.5",
            nullable = true
        )
        BigDecimal slippageVolatilityMultiplier

    ) {}
}
