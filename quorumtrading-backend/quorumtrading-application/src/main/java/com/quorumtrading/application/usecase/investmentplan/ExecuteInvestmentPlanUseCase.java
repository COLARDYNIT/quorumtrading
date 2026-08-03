package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.model.entity.InvestmentAllocation;

public interface ExecuteInvestmentPlanUseCase {
    /**
     * Run one tick of a recurring investment plan. Always writes an
     * {@link InvestmentAllocation} audit row — including for skips and failures.
     * Idempotency for plan retries is enforced at the {@code SimulationContribution} layer.
     */
    InvestmentAllocation execute(ExecuteInvestmentPlanCommand command);
}
