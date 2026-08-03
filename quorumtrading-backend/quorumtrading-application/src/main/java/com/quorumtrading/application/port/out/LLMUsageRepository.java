package com.quorumtrading.application.port.out;

import com.quorumtrading.model.valueobject.UsageSource;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository interface for querying LLM usage statistics.
 * Aggregates costs from consensus decisions and research executions.
 */
public interface LLMUsageRepository {

    /**
     * Get daily LLM usage for a user within a date range.
     * Aggregates raw costs from both consensus decisions and research executions.
     *
     * @param userId The user to get statistics for
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return List of daily usage records
     */
    List<DailyUsageRecord> getDailyUsage(UserId userId, LocalDate from, LocalDate to);

    /**
     * Get total raw LLM cost for a user within a date range.
     *
     * @param userId The user to get total for
     * @param from Start date (inclusive)
     * @param to End date (inclusive)
     * @return Total raw cost in USD
     */
    BigDecimal getTotalRawCost(UserId userId, LocalDate from, LocalDate to);

    /**
     * Log an LLM usage entry (e.g. chat cost) for daily breakdown tracking.
     *
     * @param userId     The user who incurred the cost
     * @param source     The source of the usage (CHAT, SIMULATION, RESEARCH)
     * @param rawCost    Raw cost in USD
     * @param totalTokens Total tokens consumed
     */
    void logUsage(UserId userId, UsageSource source, BigDecimal rawCost, int totalTokens);

    /**
     * Record representing daily usage aggregation.
     */
    record DailyUsageRecord(
        LocalDate date,
        BigDecimal rawCostUsd,
        int totalTokens
    ) {}
}
