package com.quorumtrading.adapter.rest.dto.response;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@RegisterForReflection
@Schema(description = "Admin user search results with budget info")
public record AdminUserSearchResponse(
    @Schema(description = "Matching users with budget details")
    List<UserWithBudget> users
) {
    @RegisterForReflection
    @Schema(description = "User info with budget details for admin search")
    public record UserWithBudget(
        String id,
        String email,
        String displayName,
        String role,
        String createdAt,
        String lastLoginAt,
        @Schema(description = "Budget limit in USD, null if unlimited")
        BigDecimal budgetLimit,
        @Schema(description = "Lifetime spend in USD")
        BigDecimal lifetimeSpend,
        @Schema(description = "Budget status: AVAILABLE, WARNING, or DEPLETED")
        String budgetStatus
    ) {}
}
