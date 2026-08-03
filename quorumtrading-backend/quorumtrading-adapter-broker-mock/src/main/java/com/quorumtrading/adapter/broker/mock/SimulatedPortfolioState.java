package com.quorumtrading.adapter.broker.mock;

import com.quorumtrading.model.entity.Position;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal state management for a simulated portfolio.
 * Thread-safe for concurrent access.
 */
class SimulatedPortfolioState {

    private Money cashAvailable;
    private final Map<Symbol, Position> positions;

    public SimulatedPortfolioState(Money initialCash) {
        this.cashAvailable = initialCash;
        this.positions = new ConcurrentHashMap<>();
    }

    /**
     * Get available cash balance.
     */
    public synchronized Money getCashAvailable() {
        return cashAvailable;
    }

    /**
     * Deduct cash (for buy orders).
     */
    public synchronized void deductCash(Money amount) {
        this.cashAvailable = cashAvailable.subtract(amount);
    }

    /**
     * Add cash (for sell orders).
     */
    public synchronized void addCash(Money amount) {
        this.cashAvailable = cashAvailable.add(amount);
    }

    /**
     * Add or update a position.
     */
    public synchronized void addPosition(Symbol symbol, BigDecimal quantity, Money pricePerUnit) {
        Position existingPosition = positions.get(symbol);

        if (existingPosition == null) {
            // Create new position with initial price
            positions.put(symbol, new Position(symbol, quantity, pricePerUnit));
        } else {
            // Update existing position with weighted average
            Position updatedPosition = existingPosition.addQuantity(quantity, pricePerUnit);
            positions.put(symbol, updatedPosition);
        }
    }

    /**
     * Reduce or remove a position.
     * @throws IllegalArgumentException if insufficient position quantity
     */
    public synchronized void reducePosition(Symbol symbol, BigDecimal quantity) {
        Position existingPosition = positions.get(symbol);

        if (existingPosition == null) {
            throw new IllegalArgumentException(
                String.format("No position exists for symbol: %s", symbol.ticker())
            );
        }

        if (existingPosition.quantity().compareTo(quantity) < 0) {
            throw new IllegalArgumentException(
                String.format("Insufficient position for %s. Have: %s, requested: %s",
                    symbol.ticker(), existingPosition.quantity(), quantity)
            );
        }

        Position updatedPosition = existingPosition.reduceQuantity(quantity);

        if (updatedPosition.quantity().compareTo(BigDecimal.ZERO) == 0) {
            // Close position completely
            positions.remove(symbol);
        } else {
            // Update position
            positions.put(symbol, updatedPosition);
        }
    }

    /**
     * Get current positions (defensive copy).
     */
    public synchronized Map<Symbol, Position> getPositions() {
        return new HashMap<>(positions);
    }

    /**
     * Get position for a specific symbol.
     */
    public synchronized Position getPosition(Symbol symbol) {
        return positions.get(symbol);
    }

    /**
     * Check if a position exists for a symbol.
     */
    public synchronized boolean hasPosition(Symbol symbol) {
        return positions.containsKey(symbol);
    }
}
