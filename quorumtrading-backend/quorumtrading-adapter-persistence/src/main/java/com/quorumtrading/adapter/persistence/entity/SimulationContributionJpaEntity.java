package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_contributions")
public class SimulationContributionJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "amount", nullable = false, precision = 20, scale = 8)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "contribution_date", nullable = false)
    private Instant contributionDate;

    @Column(name = "source_plan_id")
    private UUID sourcePlanId;

    @Column(name = "source_allocation_id")
    private UUID sourceAllocationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public SimulationContributionJpaEntity() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (currency == null) {
            currency = "USD";
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSimulationId() { return simulationId; }
    public void setSimulationId(UUID simulationId) { this.simulationId = simulationId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getContributionDate() { return contributionDate; }
    public void setContributionDate(Instant contributionDate) { this.contributionDate = contributionDate; }

    public UUID getSourcePlanId() { return sourcePlanId; }
    public void setSourcePlanId(UUID sourcePlanId) { this.sourcePlanId = sourcePlanId; }

    public UUID getSourceAllocationId() { return sourceAllocationId; }
    public void setSourceAllocationId(UUID sourceAllocationId) { this.sourceAllocationId = sourceAllocationId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
