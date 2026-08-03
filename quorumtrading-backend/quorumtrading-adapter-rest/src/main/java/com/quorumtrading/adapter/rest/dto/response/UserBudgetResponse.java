package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.dto.UserBudgetDto;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * REST response DTO for user budget information.
 */
@Schema(description = "User's AI budget information")
public record UserBudgetResponse(
    @Schema(description = "Budget ID", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    String id,

    @Schema(description = "Total budget limit in USD. Null if unlimited.", example = "50.00")
    BigDecimal limitAmount,

    @Schema(description = "Current month's spend in USD", example = "12.50", required = true)
    BigDecimal currentSpend,

    @Schema(description = "Total lifetime spend in USD", example = "25.00", required = true)
    BigDecimal lifetimeSpend,

    @Schema(description = "Remaining budget in USD. Null if unlimited.", example = "37.50")
    BigDecimal remainingBudget,

    @Schema(description = "Usage as percentage of limit (0-100+). 0 if unlimited.", example = "25.00", required = true)
    BigDecimal usagePercentage,

    @Schema(description = "Current budget status", example = "AVAILABLE", required = true, enumeration = {"AVAILABLE", "WARNING", "DEPLETED"})
    String status,

    @Schema(description = "Whether the budget is unlimited", example = "false", required = true)
    boolean isUnlimited,

    @Schema(description = "Daily spend limit in USD. Null if no limit.", example = "5.00")
    BigDecimal dailySpendLimit,

    @Schema(description = "Timestamp of the last top-up. Null if never topped up.", example = "2026-02-20T14:30:00Z")
    String lastTopUpAt,

    @Schema(description = "Today's spend in USD (billed, with markup)", example = "0.45", required = true)
    BigDecimal todaySpend,

    @Schema(description = "Last 7 days' spend in USD (billed, with markup)", example = "3.20", required = true)
    BigDecimal weeklySpend
) {

    /**
     * Convert from application DTO.
     */
    public static UserBudgetResponse fromDto(UserBudgetDto dto) {
        return new UserBudgetResponse(
            dto.id(),
            dto.limitAmount(),
            dto.currentSpend(),
            dto.lifetimeSpend(),
            dto.remainingBudget(),
            dto.usagePercentage(),
            dto.status().name(),
            dto.isUnlimited(),
            dto.dailySpendLimit(),
            dto.lastTopUpAt() != null ? dto.lastTopUpAt().toString() : null,
            dto.todaySpend(),
            dto.weeklySpend()
        );
    }
}
