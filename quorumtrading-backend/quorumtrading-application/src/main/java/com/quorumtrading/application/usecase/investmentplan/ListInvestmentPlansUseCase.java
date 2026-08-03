package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;
import java.util.Optional;

public interface ListInvestmentPlansUseCase {
    List<InvestmentPlan> listForUser(UserId userId, Optional<InvestmentPlanStatus> status);
}
