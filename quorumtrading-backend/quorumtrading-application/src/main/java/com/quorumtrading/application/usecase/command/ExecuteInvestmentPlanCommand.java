package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

import java.time.Instant;

/**
 * Fire one execution of an investment plan — either manually (REST/MCP) or
 * triggered by the scheduler in Phase 5. The {@code userId} is used to assert
 * ownership when the caller is a human operator; the scheduler passes the
 * plan's owning user.
 *
 * <p>Setting {@code advanceSchedule = false} is reserved for retries that
 * shouldn't push the next-execution-at forward.</p>
 */
public record ExecuteInvestmentPlanCommand(
        InvestmentPlanId planId,
        UserId userId,
        Instant executionDate,
        boolean advanceSchedule
) {
    public ExecuteInvestmentPlanCommand {
        if (planId == null) throw new IllegalArgumentException("planId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (executionDate == null) executionDate = Instant.now();
    }
}
