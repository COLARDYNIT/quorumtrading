package com.quorumtrading.adapter.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record McpInvestmentPlanDto(
        String id,
        String brokerAccountId,
        String budgetMode,
        String amount,
        String currency,
        Integer dayOfMonth,
        String pickerPolicy,
        Preferences preferences,
        String status,
        String nextExecutionAt,
        String lastExecutionAt,
        String createdAt,
        String updatedAt,
        /**
         * Whether the DCA scheduler runs on this instance and will act on nextExecutionAt.
         * False by default: the plan sits ACTIVE and nothing executes it until an operator
         * enables the scheduler. Say so — a plan that silently never invests looks identical
         * to one that is working.
         */
        boolean automaticExecutionEnabled,
        /** Web-app detail page for this plan. Omitted on headless instances (frontend disabled). */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String uiUrl
) {
    @RegisterForReflection
    public record Preferences(
            String riskTolerance,
            List<String> allowedTickers,
            List<String> blockedTickers,
            Integer maxStrategiesActive,
            boolean preferExistingHoldings
    ) {}
}
