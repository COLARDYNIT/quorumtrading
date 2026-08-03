package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.UserId;

public interface TerminateInvestmentPlanUseCase {
    void terminate(InvestmentPlanId planId, UserId userId);
}
