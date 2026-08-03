package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

public interface ListInvestmentAllocationsUseCase {
    /** Allocations for one plan, newest first. */
    List<InvestmentAllocation> forPlan(InvestmentPlanId planId, UserId userId, int limit);

    /** Cross-plan allocation history for the user, newest first. */
    List<InvestmentAllocation> forUser(UserId userId, int limit);
}
