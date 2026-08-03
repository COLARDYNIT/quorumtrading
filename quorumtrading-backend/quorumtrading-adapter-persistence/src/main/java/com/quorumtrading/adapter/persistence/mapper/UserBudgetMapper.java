package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.UserBudgetJpaEntity;
import com.quorumtrading.model.entity.UserBudget;
import com.quorumtrading.model.valueobject.*;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapper between UserBudget domain model and UserBudgetJpaEntity.
 */
@ApplicationScoped
public class UserBudgetMapper {

    private static final Logger LOG = Logger.getLogger(UserBudgetMapper.class);

    /** Reserved suffix marking an effort entry; '#' can never appear in a context or agent name. */
    private static final String EFFORT_SUFFIX = "#effort";

    /**
     * Convert domain UserBudget to JPA entity.
     */
    public UserBudgetJpaEntity toJpaEntity(UserBudget budget) {
        UserBudgetJpaEntity entity = new UserBudgetJpaEntity();

        entity.id = budget.getId().value();
        entity.userId = budget.getUserId().value();

        // Budget limit
        if (budget.getLimit().isPresent()) {
            BudgetLimit limit = budget.getLimit().get();
            entity.budgetLimitAmount = limit.amount();
            entity.budgetLimitCurrency = limit.currency();
        } else {
            entity.budgetLimitAmount = null;
            entity.budgetLimitCurrency = "USD";
        }

        // Current spend and lifetime spend
        entity.currentSpendAmount = budget.getCurrentSpend().amount();
        entity.lifetimeSpendAmount = budget.getLifetimeSpend();
        entity.currentPeriod = formatPeriod(budget.getCurrentSpend().period());

        // Model preferences
        entity.modelPreferences = toJsonMap(budget.getModelPreferences());

        // Daily spend limit and last top-up
        entity.dailySpendLimit = budget.getDailySpendLimit().orElse(null);
        entity.lastTopUpAt = budget.getLastTopUpAt().orElse(null);

        // Timestamps
        entity.createdAt = budget.getCreatedAt();
        entity.updatedAt = budget.getUpdatedAt();

        return entity;
    }

    /**
     * Convert JPA entity to domain UserBudget.
     */
    public UserBudget toDomain(UserBudgetJpaEntity entity) {
        UserBudgetId id = UserBudgetId.of(entity.id);
        UserId userId = new UserId(entity.userId);

        // Budget limit
        BudgetLimit limit = null;
        if (entity.budgetLimitAmount != null) {
            limit = new BudgetLimit(
                entity.budgetLimitAmount,
                entity.budgetLimitCurrency != null ? entity.budgetLimitCurrency : "USD"
            );
        }

        // Current spend
        YearMonth period = parsePeriod(entity.currentPeriod);
        MonthlySpend currentSpend = new MonthlySpend(
            entity.currentSpendAmount != null ? entity.currentSpendAmount : BigDecimal.ZERO,
            period
        );

        // Lifetime spend
        BigDecimal lifetimeSpend = entity.lifetimeSpendAmount != null ? entity.lifetimeSpendAmount : BigDecimal.ZERO;

        // Model preferences
        ModelPreferences preferences = fromJsonMap(entity.modelPreferences);

        return UserBudget.reconstitute(
            id,
            userId,
            limit,
            currentSpend,
            lifetimeSpend,
            preferences,
            entity.dailySpendLimit,
            entity.lastTopUpAt,
            entity.createdAt,
            entity.updatedAt
        );
    }

    /**
     * Convert ModelPreferences to JSON-compatible map.
     * Uses ModelPreferenceKey.toStorageKey() for keys:
     * - "BACKTESTING" for global context preferences
     * - "BACKTESTING_TECHNICAL" for per-agent preferences
     */
    private Map<String, String> toJsonMap(ModelPreferences preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> map = new HashMap<>();
        for (var entry : preferences.preferences().entrySet()) {
            map.put(entry.getKey().toStorageKey(), entry.getValue().value());
        }
        // Effort rides alongside the model under a reserved-suffix key rather than being folded
        // into the value. Keeping it out of the model string is what lets ModelId stay a pure model
        // id, and the '#' cannot collide with an agent name because fromStorageKey refuses it.
        for (var entry : preferences.efforts().entrySet()) {
            map.put(entry.getKey().toStorageKey() + EFFORT_SUFFIX, entry.getValue().name());
        }
        return map;
    }

    /**
     * Convert JSON map to ModelPreferences.
     * Handles both old format ("BACKTESTING") and new format ("BACKTESTING_TECHNICAL").
     *
     * <p>An unreadable key is logged at WARN and skipped. Skipping is grudging, not comfortable —
     * the write path now refuses unrecognised keys outright, so anything landing here came from
     * hand-edited data or a removed enum constant, and failing the whole budget load over one bad
     * entry would lock a user out of the app entirely. The WARN is what stops it being silent.</p>
     */
    private ModelPreferences fromJsonMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return ModelPreferences.EMPTY;
        }

        Map<ModelPreferenceKey, ModelId> preferences = new HashMap<>();
        Map<ModelPreferenceKey, ModelEffort> efforts = new HashMap<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            String rawKey = entry.getKey();
            boolean isEffort = rawKey.endsWith(EFFORT_SUFFIX);
            String keyPart = isEffort ? rawKey.substring(0, rawKey.length() - EFFORT_SUFFIX.length()) : rawKey;
            try {
                ModelPreferenceKey key = ModelPreferenceKey.fromStorageKey(keyPart);
                if (isEffort) {
                    ModelEffort.fromValue(entry.getValue()).ifPresentOrElse(
                            effort -> efforts.put(key, effort),
                            () -> LOG.warnf("Dropping unknown effort level '%s' for %s",
                                    entry.getValue(), keyPart));
                } else {
                    preferences.put(key, ModelId.of(entry.getValue()));
                }
            } catch (IllegalArgumentException e) {
                LOG.warnf("Dropping unreadable stored model preference key '%s': %s",
                        rawKey, e.getMessage());
            }
        }
        return new ModelPreferences(preferences, efforts);
    }

    /**
     * Format YearMonth to string (YYYY-MM).
     */
    private String formatPeriod(YearMonth period) {
        return period != null ? period.toString() : YearMonth.now().toString();
    }

    /**
     * Parse string to YearMonth.
     */
    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period);
        } catch (Exception e) {
            return YearMonth.now();
        }
    }
}
