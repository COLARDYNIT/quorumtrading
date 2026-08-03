package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.LLMUsageRepository;
import com.quorumtrading.application.port.out.UserBudgetRepository;
import com.quorumtrading.application.usecase.dto.UserBudgetDto;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.LLMUsage;
import com.quorumtrading.model.service.PlatformMarkup;
import com.quorumtrading.model.valueobject.UserId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Use case to get the current user's budget information.
 * Creates a default unlimited budget if none exists.
 */
public class GetUserBudgetUseCase {

    private final UserBudgetRepository userBudgetRepository;
    private final LLMUsageRepository llmUsageRepository;

    public GetUserBudgetUseCase(UserBudgetRepository userBudgetRepository, LLMUsageRepository llmUsageRepository) {
        this.userBudgetRepository = userBudgetRepository;
        this.llmUsageRepository = llmUsageRepository;
    }

    /**
     * Get budget for the specified user.
     * Creates an unlimited budget if none exists.
     *
     * @param userId the user's ID
     * @return the user's budget DTO
     */
    public UserBudgetDto execute(UserId userId) {
        UserBudget budget = userBudgetRepository.findByUserId(userId)
            .orElseGet(() -> {
                // Create default unlimited budget for new users
                UserBudget newBudget = UserBudget.createUnlimited(userId);
                return userBudgetRepository.save(newBudget);
            });

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Inclusive window at both ends, so seven days is today minus 6 — see
        // GetLLMUsageStatisticsUseCase. The budget page divides this by 7 for its burn rate.
        LocalDate sevenDaysAgo = today.minusDays(6);

        BigDecimal todayRaw = llmUsageRepository.getTotalRawCost(userId, today, today);
        BigDecimal weeklyRaw = llmUsageRepository.getTotalRawCost(userId, sevenDaysAgo, today);

        BigDecimal todaySpend = applyMarkup(todayRaw);
        BigDecimal weeklySpend = applyMarkup(weeklyRaw);

        return UserBudgetDto.fromDomain(budget, todaySpend, weeklySpend);
    }

    /**
     * Scale 8 matches {@code llm_usage_log.raw_cost_usd DECIMAL(18,8)} and
     * {@code GetLLMUsageStatisticsUseCase}. The budget page and the usage page render the same
     * underlying number, so they must not round it at different scales first.
     */
    private BigDecimal applyMarkup(BigDecimal rawCost) {
        if (rawCost == null) {
            return BigDecimal.ZERO;
        }
        return rawCost.multiply(PlatformMarkup.rate()).setScale(8, RoundingMode.HALF_UP);
    }
}
