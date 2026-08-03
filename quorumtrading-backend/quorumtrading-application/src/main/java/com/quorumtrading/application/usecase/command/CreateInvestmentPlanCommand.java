package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;

public record CreateInvestmentPlanCommand(
        UserId userId,
        BrokerAccountId brokerAccountId,
        BudgetMode budgetMode,
        Money amount,             // may be null only when budgetMode == ALL_AVAILABLE
        int dayOfMonth,
        PickerPolicy pickerPolicy,
        InvestmentPlanPreferences preferences,
        /** Must be true when targeting an ALPACA_LIVE broker. Defends against accidental live wiring. */
        boolean confirmLive
) {
    public CreateInvestmentPlanCommand {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        if (brokerAccountId == null) throw new IllegalArgumentException("brokerAccountId is required");
        if (budgetMode == null) throw new IllegalArgumentException("budgetMode is required");
        if (pickerPolicy == null) throw new IllegalArgumentException("pickerPolicy is required");
    }
}
