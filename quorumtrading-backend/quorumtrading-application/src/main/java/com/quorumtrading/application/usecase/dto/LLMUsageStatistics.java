package com.quorumtrading.application.usecase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for LLM usage statistics aggregated by time period.
 * Used to display usage costs to users in the settings page.
 */
public record LLMUsageStatistics(
    List<DailyUsage> dailyUsage,
    BigDecimal weeklyTotalUsd,
    BigDecimal monthlyTotalUsd,
    BigDecimal allTimeTotalUsd
) {

    /**
     * Daily usage breakdown showing raw and billed costs.
     */
    public record DailyUsage(
        LocalDate date,
        BigDecimal rawCostUsd,
        BigDecimal billedCostUsd,
        int totalTokens
    ) {}

    /**
     * Create empty statistics when no usage data exists.
     */
    public static LLMUsageStatistics empty() {
        return new LLMUsageStatistics(
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }
}
