package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.application.usecase.command.CreateInvestmentPlanCommand;
import com.quorumtrading.model.entity.InvestmentPlan;

public interface CreateInvestmentPlanUseCase {
    InvestmentPlan execute(CreateInvestmentPlanCommand command);
}
