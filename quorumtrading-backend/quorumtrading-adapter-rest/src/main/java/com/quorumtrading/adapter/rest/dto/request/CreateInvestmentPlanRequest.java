package com.quorumtrading.adapter.rest.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Create a recurring investment plan")
public record CreateInvestmentPlanRequest(
        @Schema(required = true) String brokerAccountId,
        @Schema(required = true, enumeration = {"FIXED_AMOUNT", "FIXED_OR_PARTIAL", "ALL_AVAILABLE"}) String budgetMode,
        @Schema(description = "Required unless budgetMode = ALL_AVAILABLE", example = "200.00") BigDecimal amount,
        @Schema(example = "USD") String currency,
        @Schema(required = true, description = "Day of month (1-28)", example = "1") Integer dayOfMonth,
        @Schema(required = true, enumeration = {"AI_PICK"}, example = "AI_PICK") String pickerPolicy,
        PreferencesPayload preferences,
        @Schema(description = "Required true when broker is ALPACA_LIVE", example = "false") boolean confirmLive
) {
    public record PreferencesPayload(
            String riskTolerance,
            List<String> allowedTickers,
            List<String> blockedTickers,
            Integer maxStrategiesActive,
            Boolean preferExistingHoldings
    ) {}
}
