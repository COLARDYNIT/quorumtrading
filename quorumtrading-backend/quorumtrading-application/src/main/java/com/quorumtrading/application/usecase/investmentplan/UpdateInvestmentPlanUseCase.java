package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.model.entity.InvestmentPlan;

public interface UpdateInvestmentPlanUseCase {
    InvestmentPlan execute(UpdateInvestmentPlanCommand command);
}
