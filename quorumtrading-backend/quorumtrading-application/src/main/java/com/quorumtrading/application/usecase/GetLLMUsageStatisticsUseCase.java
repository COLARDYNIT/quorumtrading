package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.LLMUsageRepository.DailyUsageRecord;
import com.quorumtrading.application.usecase.dto.LLMUsageStatistics;
import com.quorumtrading.application.usecase.dto.LLMUsageStatistics.DailyUsage;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.UserId;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Use case for retrieving LLM usage statistics for a user.
 * Aggregates costs from consensus decisions and research executions,
 * applying the platform markup rate from {@link PlatformMarkup}.
 */
public class GetLLMUsageStatisticsUseCase {

    private static final Logger LOG = Logger.getLogger(GetLLMUsageStatisticsUseCase.class);

    private final LLMUsageRepository llmUsageRepository;

    public GetLLMUsageStatisticsUseCase(LLMUsageRepository llmUsageRepository) {
        this.llmUsageRepository = llmUsageRepository;
    }

    /**
     * Get LLM usage statistics for a user.
     * Returns daily breakdown for the last 30 days plus weekly and monthly totals.
     *
     * @param userId The user to get statistics for
     * @return Usage statistics with daily, weekly, and monthly breakdowns
     */
    public LLMUsageStatistics execute(UserId userId) {
        if (userId == null) {
            LOG.warn("GetLLMUsageStatisticsUseCase called with null userId");
            return LLMUsageStatistics.empty();
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate thirtyDaysAgo = today.minusDays(30);
        // Seven days means seven days. The repository window is inclusive at both ends, so the
        // first day of a 7-day window is today minus 6 — minusDays(7) spans eight calendar days,
        // and the frontend divides this total by 7 to advertise a daily burn rate.
        LocalDate sevenDaysAgo = today.minusDays(6);
        LocalDate startOfMonth = today.withDayOfMonth(1);

        // Get daily usage for last 30 days
        List<DailyUsageRecord> dailyRecords = llmUsageRepository.getDailyUsage(userId, thirtyDaysAgo, today);

        // Convert to DTOs with billed cost calculated using markup rate
        List<DailyUsage> dailyUsage = dailyRecords.stream()
            .map(record -> new DailyUsage(
                record.date(),
                record.rawCostUsd(),
                applyMarkup(record.rawCostUsd()),
                record.totalTokens()
            ))
            .toList();

        // Calculate totals
        BigDecimal weeklyRawTotal = llmUsageRepository.getTotalRawCost(userId, sevenDaysAgo, today);
        BigDecimal monthlyRawTotal = llmUsageRepository.getTotalRawCost(userId, startOfMonth, today);
        BigDecimal allTimeRawTotal = llmUsageRepository.getTotalRawCost(userId, LocalDate.of(2020, 1, 1), today);

        LOG.debugf("LLM usage for user %s: weekly=%.6f, monthly=%.6f, allTime=%.6f (raw USD)",
            userId.value(), weeklyRawTotal, monthlyRawTotal, allTimeRawTotal);

        return new LLMUsageStatistics(
            dailyUsage,
            applyMarkup(weeklyRawTotal),
            applyMarkup(monthlyRawTotal),
            applyMarkup(allTimeRawTotal)
        );
    }

    /**
     * Apply the platform markup rate to a raw cost.
     */
    private BigDecimal applyMarkup(BigDecimal rawCost) {
        if (rawCost == null) {
            return BigDecimal.ZERO;
        }
        return rawCost.multiply(PlatformMarkup.rate()).setScale(8, RoundingMode.HALF_UP);
    }
}
