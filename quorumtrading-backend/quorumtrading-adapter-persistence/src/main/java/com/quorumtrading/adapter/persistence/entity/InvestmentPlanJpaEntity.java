package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investment_plans")
public class InvestmentPlanJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "broker_account_id", nullable = false)
    private UUID brokerAccountId;

    @Column(name = "budget_mode", nullable = false, length = 30)
    private String budgetMode;

    @Column(name = "amount", precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "day_of_month", nullable = false)
    private short dayOfMonth;

    @Column(name = "picker_policy", nullable = false, length = 40)
    private String pickerPolicy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", nullable = false, columnDefinition = "jsonb")
    private String preferences;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "next_execution_at")
    private Instant nextExecutionAt;

    @Column(name = "last_execution_at")
    private Instant lastExecutionAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public InvestmentPlanJpaEntity() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
        if (currency == null) currency = "USD";
        if (preferences == null) preferences = "{}";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getBrokerAccountId() { return brokerAccountId; }
    public void setBrokerAccountId(UUID brokerAccountId) { this.brokerAccountId = brokerAccountId; }
    public String getBudgetMode() { return budgetMode; }
    public void setBudgetMode(String budgetMode) { this.budgetMode = budgetMode; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public short getDayOfMonth() { return dayOfMonth; }
    public void setDayOfMonth(short dayOfMonth) { this.dayOfMonth = dayOfMonth; }
    public String getPickerPolicy() { return pickerPolicy; }
    public void setPickerPolicy(String pickerPolicy) { this.pickerPolicy = pickerPolicy; }
    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getNextExecutionAt() { return nextExecutionAt; }
    public void setNextExecutionAt(Instant nextExecutionAt) { this.nextExecutionAt = nextExecutionAt; }
    public Instant getLastExecutionAt() { return lastExecutionAt; }
    public void setLastExecutionAt(Instant lastExecutionAt) { this.lastExecutionAt = lastExecutionAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
