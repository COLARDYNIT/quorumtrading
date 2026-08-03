package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

public interface GetInvestmentPlanUseCase {
    InvestmentPlan getOwnedBy(InvestmentPlanId planId, UserId userId);
}
