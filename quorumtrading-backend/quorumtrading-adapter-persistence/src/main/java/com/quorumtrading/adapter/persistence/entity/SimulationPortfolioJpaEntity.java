package com.quorumtrading.adapter.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for SimulationPortfolio persistence.
 *
 * Maps to the simulation_portfolios table created by Flyway migrations.
 * This is SEPARATE from the domain SimulationPortfolio to maintain hexagonal architecture boundaries.
 *
 * Uses standard JPA (not Panache) for cross-module compatibility.
 * The simulation_id is the primary key (one portfolio per simulation).
 */
@Entity
@Table(name = "simulation_portfolios")
public class SimulationPortfolioJpaEntity {

    /**
     * Primary key - references simulations.id.
     * One portfolio per simulation (1:1 relationship).
     */
    @Id
    @Column(name = "simulation_id")
    private UUID simulationId;

    /**
     * Foreign key to broker_accounts table.
     */
    @Column(name = "broker_account_id", nullable = false)
    private UUID brokerAccountId;

    /**
     * Current positions as JSONB.
     * Contains position data including symbol, quantity, average cost, etc.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "positions", columnDefinition = "jsonb", nullable = false)
    private String positions;

    /**
     * Cash available for trading.
     */
    @Column(name = "cash_available", nullable = false, precision = 20, scale = 8)
    private BigDecimal cashAvailable;

    /**
     * Cash reserved for pending orders.
     */
    @Column(name = "cash_reserved", nullable = false, precision = 20, scale = 8)
    private BigDecimal cashReserved;

    /**
     * Total realized profit/loss.
     */
    @Column(name = "realized_pnl", nullable = false, precision = 20, scale = 8)
    private BigDecimal realizedPnl;

    /**
     * Settlement currency for {@code cashAvailable} / {@code cashReserved} /
     * {@code realizedPnl} (V119). USD for Alpaca, EUR for Saxo/EU accounts.
     * Defaults to {@code "USD"} so legacy rows and the no-arg path stay byte-identical.
     */
    @Column(name = "cash_currency", nullable = false, length = 10)
    private String cashCurrency;

    /**
     * Last update timestamp.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Defensive default so a portfolio persisted without an explicit currency (legacy
     * code paths) lands as USD rather than violating the NOT NULL column.
     */
    @PrePersist
    void applyDefaults() {
        if (cashCurrency == null) {
            cashCurrency = "USD";
        }
    }

    /**
     * Default constructor for JPA.
     */
    public SimulationPortfolioJpaEntity() {
    }

    // Getters and setters

    public UUID getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(UUID simulationId) {
        this.simulationId = simulationId;
    }

    public UUID getBrokerAccountId() {
        return brokerAccountId;
    }

    public void setBrokerAccountId(UUID brokerAccountId) {
        this.brokerAccountId = brokerAccountId;
    }

    public String getPositions() {
        return positions;
    }

    public void setPositions(String positions) {
        this.positions = positions;
    }

    public BigDecimal getCashAvailable() {
        return cashAvailable;
    }

    public void setCashAvailable(BigDecimal cashAvailable) {
        this.cashAvailable = cashAvailable;
    }

    public BigDecimal getCashReserved() {
        return cashReserved;
    }

    public void setCashReserved(BigDecimal cashReserved) {
        this.cashReserved = cashReserved;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public void setRealizedPnl(BigDecimal realizedPnl) {
        this.realizedPnl = realizedPnl;
    }

    public String getCashCurrency() {
        return cashCurrency;
    }

    public void setCashCurrency(String cashCurrency) {
        this.cashCurrency = cashCurrency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
