package com.quorumtrading.adapter.rest.dto.request;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Partial update — pass only the fields you want to change. Omitted fields keep "
        + "their stored value, including individual picker preferences.")
public record UpdateInvestmentPlanRequest(
        String brokerAccountId,
        @Schema(enumeration = {"FIXED_AMOUNT", "FIXED_OR_PARTIAL", "ALL_AVAILABLE"}) String budgetMode,
        BigDecimal amount,
        String currency,
        @Schema(description = "1-28") Integer dayOfMonth,
        @Schema(description = "Preferences are merged field by field: a preference you omit keeps its "
                + "stored value. To clear a ticker list send it as []; to clear riskTolerance send \"\".")
        CreateInvestmentPlanRequest.PreferencesPayload preferences
) {}
