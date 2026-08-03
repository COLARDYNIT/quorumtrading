package com.quorumtrading.application.usecase.investmentplan;

import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.UserId;

public interface GetInvestmentAllocationUseCase {
    InvestmentAllocation getOwnedBy(InvestmentAllocationId id, UserId userId);
}
