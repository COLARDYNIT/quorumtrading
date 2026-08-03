package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RegisterForReflection
@Schema(description = "Admin dashboard data including counts, simulation breakdown, recent users, and budget summary")
public record AdminDashboardResponse(
    @Schema(description = "Total number of users")
    long userCount,

    @Schema(description = "Total number of market research configurations")
    long researchCount,

    @Schema(description = "Total number of strategies")
    long strategyCount,

    @Schema(description = "Total number of simulations")
    long simulationCount,

    @Schema(description = "Simulation count grouped by status")
    Map<String, Long> simulationsByStatus,

    @Schema(description = "Most recently registered users")
    List<AdminUserInfo> recentUsers,

    @Schema(description = "Aggregate budget statistics")
    BudgetSummary budgetSummary
) {
    @RegisterForReflection
    @Schema(description = "Summary user info for admin views")
    public record AdminUserInfo(
        String id,
        String email,
        String displayName,
        String role,
        String createdAt,
        String lastLoginAt
    ) {}

    @RegisterForReflection
    @Schema(description = "Aggregate budget statistics across all users")
    public record BudgetSummary(
        @Schema(description = "Total budget limit allocated across all users (USD)")
        BigDecimal totalAllocated,

        @Schema(description = "Total lifetime spend across all users (USD)")
        BigDecimal totalLifetimeSpend,

        @Schema(description = "Number of users with depleted budgets")
        long depletedCount
    ) {}
}
