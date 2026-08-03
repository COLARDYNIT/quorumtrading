package com.quorumtrading.adapter.rest.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Request DTO for topping up a user's budget.
 */
@Schema(description = "Request to top up AI budget")
public record TopUpBudgetRequest(
    @Schema(description = "Amount to add in USD", example = "10.00", required = true)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least $0.01")
    BigDecimal amount
) {}
