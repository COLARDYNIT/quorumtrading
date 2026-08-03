package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

public interface ResumeInvestmentPlanUseCase {
    InvestmentPlan resume(InvestmentPlanId planId, UserId userId);
}
