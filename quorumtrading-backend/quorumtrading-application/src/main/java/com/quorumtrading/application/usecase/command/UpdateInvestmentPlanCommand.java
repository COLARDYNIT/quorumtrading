package com.quorumtrading.application.usecase.command;

import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferencesPatch;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;

import java.util.Optional;

/**
 * Partial update — every field is optional. Pass {@code Optional.empty()} to
 * leave a field unchanged; pass a present value (including a sentinel zero, etc.)
 * to write it through.
 *
 * <p>Preferences travel as an {@link InvestmentPlanPreferencesPatch} rather than a whole
 * {@code InvestmentPlanPreferences}, so that a caller who names one preference does not silently
 * erase the others — see that type for why.</p>
 */
public record UpdateInvestmentPlanCommand(
        InvestmentPlanId planId,
        UserId userId,
        Optional<BrokerAccountId> brokerAccountId,
        Optional<BudgetMode> budgetMode,
        Optional<Money> amount,
        Optional<Integer> dayOfMonth,
        Optional<InvestmentPlanPreferencesPatch> preferences,
        /**
         * Acknowledges that the plan will trade real money. Required when repointing a plan at a
         * live broker account, mirroring {@link CreateInvestmentPlanCommand#confirmLive()} — without
         * it a plan created against a mock or paper account could be moved onto a live one and start
         * spending real money on the next scheduled tick with no acknowledgement.
         */
        boolean confirmLive
) {
    public UpdateInvestmentPlanCommand {
        if (planId == null) throw new IllegalArgumentException("planId is required");
        if (userId == null) throw new IllegalArgumentException("userId is required");
        brokerAccountId = brokerAccountId == null ? Optional.empty() : brokerAccountId;
        budgetMode = budgetMode == null ? Optional.empty() : budgetMode;
        amount = amount == null ? Optional.empty() : amount;
        dayOfMonth = dayOfMonth == null ? Optional.empty() : dayOfMonth;
        preferences = preferences == null ? Optional.empty() : preferences;
    }

    /** Backward-compatible constructor; defaults to withholding live-trading consent. */
    public UpdateInvestmentPlanCommand(
            InvestmentPlanId planId,
            UserId userId,
            Optional<BrokerAccountId> brokerAccountId,
            Optional<BudgetMode> budgetMode,
            Optional<Money> amount,
            Optional<Integer> dayOfMonth,
            Optional<InvestmentPlanPreferencesPatch> preferences) {
        this(planId, userId, brokerAccountId, budgetMode, amount, dayOfMonth, preferences, false);
    }
}
