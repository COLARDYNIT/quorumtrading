package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "A recurring investment plan and its current schedule state")
public record InvestmentPlanResponse(
        String id,
        String userId,
        String brokerAccountId,
        String budgetMode,
        BigDecimal amount,
        String currency,
        Integer dayOfMonth,
        String pickerPolicy,
        PreferencesPayload preferences,
        String status,
        Instant nextExecutionAt,
        Instant lastExecutionAt,
        Instant createdAt,
        Instant updatedAt,
        boolean automaticExecutionEnabled
) {
    @Schema(description = "User-supplied constraints applied to each picker run")
    public record PreferencesPayload(
            String riskTolerance,
            List<String> allowedTickers,
            List<String> blockedTickers,
            Integer maxStrategiesActive,
            boolean preferExistingHoldings
    ) {
        public static PreferencesPayload from(InvestmentPlanPreferences p) {
            if (p == null) return new PreferencesPayload(null, List.of(), List.of(), null, true);
            return new PreferencesPayload(
                    p.riskTolerance(),
                    p.allowedTickers(),
                    p.blockedTickers(),
                    p.maxStrategiesActive(),
                    p.preferExistingHoldings());
        }
    }

    /**
     * @param automaticExecutionEnabled whether the DCA scheduler is running on this instance. When
     *     false — the default everywhere — {@code nextExecutionAt} describes the schedule the plan
     *     <em>would</em> follow, and nothing will act on it. Callers must render the difference:
     *     an ACTIVE plan that silently never invests is the worst failure this feature has.
     */
    public static InvestmentPlanResponse fromDomain(InvestmentPlan plan,
                                                    boolean automaticExecutionEnabled) {
        return new InvestmentPlanResponse(
                plan.getId().value().toString(),
                plan.getUserId().value().toString(),
                plan.getBrokerAccountId().value().toString(),
                plan.getBudgetMode().name(),
                plan.getAmount() != null ? plan.getAmount().amount() : null,
                plan.getAmount() != null ? plan.getAmount().currency().name() : "USD",
                plan.getDayOfMonth(),
                plan.getPickerPolicy().name(),
                PreferencesPayload.from(plan.getPreferences()),
                plan.getStatus().name(),
                plan.getNextExecutionAt(),
                plan.getLastExecutionAt(),
                plan.getCreatedAt(),
                plan.getUpdatedAt(),
                automaticExecutionEnabled);
    }
}
