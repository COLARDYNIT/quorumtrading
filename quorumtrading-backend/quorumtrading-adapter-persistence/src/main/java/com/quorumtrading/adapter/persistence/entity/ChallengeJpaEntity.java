package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenges")
public class ChallengeJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "slug", length = 255, nullable = false, unique = true)
    private String slug;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ticker", length = 20, nullable = false)
    private String ticker;

    @Column(name = "initial_cash", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialCash;

    @Column(name = "simulation_start_date", nullable = false)
    private Instant simulationStartDate;

    @Column(name = "simulation_end_date", nullable = false)
    private Instant simulationEndDate;

    @Column(name = "participation_open", nullable = false)
    private Instant participationOpen;

    @Column(name = "participation_close", nullable = false)
    private Instant participationClose;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "strategies_public", nullable = false)
    private boolean strategiesPublic;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public BigDecimal getInitialCash() { return initialCash; }
    public void setInitialCash(BigDecimal initialCash) { this.initialCash = initialCash; }
    public Instant getSimulationStartDate() { return simulationStartDate; }
    public void setSimulationStartDate(Instant simulationStartDate) { this.simulationStartDate = simulationStartDate; }
    public Instant getSimulationEndDate() { return simulationEndDate; }
    public void setSimulationEndDate(Instant simulationEndDate) { this.simulationEndDate = simulationEndDate; }
    public Instant getParticipationOpen() { return participationOpen; }
    public void setParticipationOpen(Instant participationOpen) { this.participationOpen = participationOpen; }
    public Instant getParticipationClose() { return participationClose; }
    public void setParticipationClose(Instant participationClose) { this.participationClose = participationClose; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public boolean isStrategiesPublic() { return strategiesPublic; }
    public void setStrategiesPublic(boolean strategiesPublic) { this.strategiesPublic = strategiesPublic; }
}
