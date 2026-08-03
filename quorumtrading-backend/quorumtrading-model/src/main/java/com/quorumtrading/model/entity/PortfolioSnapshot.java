package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Pure domain entity representing a snapshot of a portfolio at a specific point in time.
 * This is the core business object without persistence concerns.
 *
 * The positions field contains the actual portfolio positions as domain objects.
 * JSON serialization/deserialization is handled by the persistence adapter (PortfolioMapper).
 */
public class PortfolioSnapshot {

    private Long id;
    private SimulationId simulationId;
    private Instant timestamp;
    private Map<Symbol, Position> positions; // Actual domain positions
    private Money totalValue;
    private Money cashAvailable;

    public PortfolioSnapshot() {
    }

    public PortfolioSnapshot(
            SimulationId simulationId,
            Instant timestamp,
            Map<Symbol, Position> positions,
            Money totalValue,
            Money cashAvailable
    ) {
        // Validate totalValue - must never be null, zero, or negative
        // A $0 portfolio value indicates corrupted data (API failure, missing prices, etc.)
        if (totalValue == null) {
            throw new IllegalArgumentException(
                "PortfolioSnapshot totalValue cannot be null for simulation " +
                (simulationId != null ? simulationId.value() : "unknown"));
        }
        if (totalValue.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "PortfolioSnapshot totalValue must be positive, got " + totalValue.amount() +
                " for simulation " + (simulationId != null ? simulationId.value() : "unknown") +
                ". This typically indicates an API failure or missing market data.");
        }

        this.simulationId = simulationId;
        this.timestamp = timestamp;
        this.positions = positions != null ? positions : Collections.emptyMap();
        this.totalValue = totalValue;
        this.cashAvailable = cashAvailable;
    }

    // Business methods
    public boolean hasPositions() {
        return positions != null && !positions.isEmpty();
    }

    public boolean isAllCash() {
        return !hasPositions() && cashAvailable != null && cashAvailable.isPositive();
    }

    public Money getInvestedAmount() {
        if (totalValue == null || cashAvailable == null) {
            return Money.ZERO_USD;
        }
        return totalValue.subtract(cashAvailable);
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public SimulationId getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(SimulationId simulationId) {
        this.simulationId = simulationId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<Symbol, Position> getPositions() {
        return positions != null ? Collections.unmodifiableMap(positions) : Collections.emptyMap();
    }

    public void setPositions(Map<Symbol, Position> positions) {
        this.positions = positions;
    }

    public Money getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(Money totalValue) {
        this.totalValue = totalValue;
    }

    public Money getCashAvailable() {
        return cashAvailable;
    }

    public void setCashAvailable(Money cashAvailable) {
        this.cashAvailable = cashAvailable;
    }
}
