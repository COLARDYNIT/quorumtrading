package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.application.usecase.dto.LLMUsageStatistics;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * REST response DTO for LLM usage statistics.
 */
@Schema(description = "LLM usage statistics for the current user")
public record LLMUsageStatisticsResponse(
    @Schema(description = "Daily usage breakdown for the last 30 days", required = true)
    List<DailyUsageResponse> dailyUsage,

    @Schema(description = "Total billed cost for the last 7 days in USD", example = "0.15234500", required = true)
    BigDecimal weeklyTotalUsd,

    @Schema(description = "Total billed cost for the current month in USD", example = "0.45678900", required = true)
    BigDecimal monthlyTotalUsd,

    @Schema(description = "Total billed cost all time in USD", example = "12.34567890", required = true)
    BigDecimal allTimeTotalUsd
) {

    /**
     * Daily usage breakdown.
     */
    @Schema(description = "Usage data for a single day")
    public record DailyUsageResponse(
        @Schema(description = "Date of usage", example = "2024-01-15", required = true)
        LocalDate date,

        @Schema(description = "Raw cost from LLM provider in USD", example = "0.00123400", required = true)
        BigDecimal rawCostUsd,

        @Schema(description = "Billed cost (raw + markup) in USD", example = "0.00160420", required = true)
        BigDecimal billedCostUsd,

        @Schema(description = "Total tokens used", example = "1234", required = true)
        int totalTokens
    ) {
        public static DailyUsageResponse fromDomain(LLMUsageStatistics.DailyUsage usage) {
            return new DailyUsageResponse(
                usage.date(),
                usage.rawCostUsd(),
                usage.billedCostUsd(),
                usage.totalTokens()
            );
        }
    }

    /**
     * Convert domain DTO to REST response.
     */
    public static LLMUsageStatisticsResponse fromDomain(LLMUsageStatistics statistics) {
        List<DailyUsageResponse> dailyResponses = statistics.dailyUsage().stream()
            .map(DailyUsageResponse::fromDomain)
            .toList();

        return new LLMUsageStatisticsResponse(
            dailyResponses,
            statistics.weeklyTotalUsd(),
            statistics.monthlyTotalUsd(),
            statistics.allTimeTotalUsd()
        );
    }
}
