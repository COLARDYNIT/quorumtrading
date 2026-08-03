package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request DTO for creating a new strategy.
 * Version is managed by Hibernate @Version for optimistic locking.
 */
@Schema(description = "Request to create a new trading strategy")
public record CreateStrategyRequest(
    @NotBlank(message = "Strategy name is required")
    @Size(max = 255, message = "Strategy name must not exceed 255 characters")
    @Schema(description = "Strategy name",
            example = "BTC Momentum Strategy",
            required = true)
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Optional human-readable description for the strategy library",
            example = "Momentum-based BTC strategy using RSI and MACD crossovers")
    String description,

    @NotBlank(message = "Markdown content is required")
    @Size(max = 512000, message = "Markdown content must not exceed 500KB")
    @Schema(description = "Strategy configuration in markdown format",
            example = """
                # BTC Momentum Strategy

                ## Consensus Method
                MAJORITY

                ## Evaluation Interval Hours
                6

                ## Rules
                - Buy when RSI < 30 and MACD crosses above signal line
                - Sell when RSI > 70 or MACD crosses below signal line
                - Maximum position size: 30% of portfolio
                """,
            required = true)
    String markdownContent
) {}
