package com.quorumtrading.adapter.persistence.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for Portfolio persistence.
 *
 * This is SEPARATE from the domain Portfolio entity to maintain
 * hexagonal architecture boundaries.
 *
 * Uses BIGSERIAL primary key with explicit sequence name configuration
 * to match PostgreSQL's default naming convention.
 */
@Entity
@Table(name = "portfolio_snapshots")
public class PortfolioJpaEntity extends PanacheEntityBase {

    // Override PanacheEntity's id field to configure the correct sequence name
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "portfolio_snapshots_seq_gen")
    @SequenceGenerator(name = "portfolio_snapshots_seq_gen", sequenceName = "portfolio_snapshots_id_seq", allocationSize = 1)
    public Long id;

    @Column(name = "simulation_id", nullable = false)
    private UUID simulationId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "positions", columnDefinition = "jsonb")
    private String positionsJson;  // JSON representation of positions map

    @Column(name = "total_value_usd", precision = 20, scale = 2)
    private BigDecimal totalValueUsd;

    @Column(name = "cash_available", precision = 20, scale = 2)
    private BigDecimal cashAvailable;

    // Getters and setters
    public UUID getSimulationId() { return simulationId; }
    public void setSimulationId(UUID simulationId) { this.simulationId = simulationId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getPositionsJson() { return positionsJson; }
    public void setPositionsJson(String positionsJson) { this.positionsJson = positionsJson; }

    public BigDecimal getTotalValueUsd() { return totalValueUsd; }
    public void setTotalValueUsd(BigDecimal totalValueUsd) { this.totalValueUsd = totalValueUsd; }

    public BigDecimal getCashAvailable() { return cashAvailable; }
    public void setCashAvailable(BigDecimal cashAvailable) { this.cashAvailable = cashAvailable; }
}
