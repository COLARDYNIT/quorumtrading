package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "investment_allocations")
public class InvestmentAllocationJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "execution_date", nullable = false)
    private Instant executionDate;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "intended_amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal intendedAmount;

    @Column(name = "actual_amount", precision = 20, scale = 8)
    private BigDecimal actualAmount;

    @Column(name = "deployed_amount", precision = 20, scale = 8)
    private BigDecimal deployedAmount;

    @Column(name = "fractional_constraint")
    private Boolean fractionalConstraint;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "chosen_strategy_id")
    private UUID chosenStrategyId;

    @Column(name = "picker_reasoning", columnDefinition = "text")
    private String pickerReasoning;

    @Column(name = "picker_llm_cost_usd", nullable = false, precision = 20, scale = 8)
    private BigDecimal pickerLlmCostUsd;

    @Column(name = "action", length = 30)
    private String action;

    @Column(name = "target_simulation_id")
    private UUID targetSimulationId;

    @Column(name = "contribution_id")
    private UUID contributionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "broker_order_refs", nullable = false, columnDefinition = "jsonb")
    private String brokerOrderRefs;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public InvestmentAllocationJpaEntity() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (currency == null) currency = "USD";
        if (brokerOrderRefs == null) brokerOrderRefs = "[]";
        if (pickerLlmCostUsd == null) pickerLlmCostUsd = BigDecimal.ZERO;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Instant getExecutionDate() { return executionDate; }
    public void setExecutionDate(Instant executionDate) { this.executionDate = executionDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getIntendedAmount() { return intendedAmount; }
    public void setIntendedAmount(BigDecimal intendedAmount) { this.intendedAmount = intendedAmount; }
    public BigDecimal getActualAmount() { return actualAmount; }
    public void setActualAmount(BigDecimal actualAmount) { this.actualAmount = actualAmount; }
    public BigDecimal getDeployedAmount() { return deployedAmount; }
    public void setDeployedAmount(BigDecimal deployedAmount) { this.deployedAmount = deployedAmount; }
    public Boolean getFractionalConstraint() { return fractionalConstraint; }
    public void setFractionalConstraint(Boolean fractionalConstraint) { this.fractionalConstraint = fractionalConstraint; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public UUID getChosenStrategyId() { return chosenStrategyId; }
    public void setChosenStrategyId(UUID chosenStrategyId) { this.chosenStrategyId = chosenStrategyId; }
    public String getPickerReasoning() { return pickerReasoning; }
    public void setPickerReasoning(String pickerReasoning) { this.pickerReasoning = pickerReasoning; }
    public BigDecimal getPickerLlmCostUsd() { return pickerLlmCostUsd; }
    public void setPickerLlmCostUsd(BigDecimal pickerLlmCostUsd) { this.pickerLlmCostUsd = pickerLlmCostUsd; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public UUID getTargetSimulationId() { return targetSimulationId; }
    public void setTargetSimulationId(UUID targetSimulationId) { this.targetSimulationId = targetSimulationId; }
    public UUID getContributionId() { return contributionId; }
    public void setContributionId(UUID contributionId) { this.contributionId = contributionId; }
    public String getBrokerOrderRefs() { return brokerOrderRefs; }
    public void setBrokerOrderRefs(String brokerOrderRefs) { this.brokerOrderRefs = brokerOrderRefs; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
