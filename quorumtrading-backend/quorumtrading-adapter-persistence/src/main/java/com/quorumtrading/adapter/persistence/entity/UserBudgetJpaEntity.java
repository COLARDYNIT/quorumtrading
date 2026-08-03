package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for user AI budget configuration.
 */
@Entity
@Table(name = "user_budgets")
public class UserBudgetJpaEntity extends PanacheEntityBase {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    public UUID userId;

    @Column(name = "budget_limit_amount", precision = 10, scale = 2)
    public BigDecimal budgetLimitAmount;

    @Column(name = "budget_limit_currency", length = 3)
    public String budgetLimitCurrency = "USD";

    @Column(name = "current_spend_amount", precision = 10, scale = 6, nullable = false)
    public BigDecimal currentSpendAmount = BigDecimal.ZERO;

    @Column(name = "lifetime_spend_amount", precision = 10, scale = 6, nullable = false)
    public BigDecimal lifetimeSpendAmount = BigDecimal.ZERO;

    @Column(name = "current_period", length = 7, nullable = false)
    public String currentPeriod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_preferences", columnDefinition = "jsonb")
    public Map<String, String> modelPreferences;

    @Column(name = "daily_spend_limit", precision = 10, scale = 2)
    public BigDecimal dailySpendLimit;

    @Column(name = "last_top_up_at")
    public Instant lastTopUpAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (currentSpendAmount == null) {
            currentSpendAmount = BigDecimal.ZERO;
        }
        if (lifetimeSpendAmount == null) {
            lifetimeSpendAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
