package com.quorumtrading.adapter.rest.dto.response;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Result of selling a position")
public record SellPositionResponse(
    @Schema(description = "Whether the sell was successful") boolean success,
    @Schema(description = "Human-readable result message") String message,
    @Schema(description = "Quantity that was filled") BigDecimal filledQuantity,
    @Schema(description = "Execution price per unit") BigDecimal executionPrice
) {}
