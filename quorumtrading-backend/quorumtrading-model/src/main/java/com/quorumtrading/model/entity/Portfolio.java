package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record Portfolio(
    SimulationId simulationId,
    Map<Symbol, Position> positions,
    Money cashAvailable
) {

    public Portfolio {
        if (simulationId == null) {
            throw new IllegalArgumentException("SimulationId cannot be null");
        }
        if (positions == null) {
            throw new IllegalArgumentException("Positions cannot be null");
        }
        if (cashAvailable == null) {
            throw new IllegalArgumentException("Cash cannot be null");
        }
        // Defensive copy
        positions = new HashMap<>(positions);
    }

    @Override
    public Map<Symbol, Position> positions() {
        return Collections.unmodifiableMap(positions);
    }

    public Portfolio buy(Symbol symbol, BigDecimal quantity, Money pricePerUnit) {
        Money totalCost = pricePerUnit.multiply(quantity);

        if (cashAvailable.isLessThan(totalCost)) {
            throw new IllegalStateException("Insufficient funds to buy " + quantity + " " + symbol);
        }

        Money newCash = cashAvailable.subtract(totalCost);
        Map<Symbol, Position> newPositions = new HashMap<>(positions);

        Position existingPosition = newPositions.get(symbol);
        if (existingPosition != null) {
            newPositions.put(symbol, existingPosition.addQuantity(quantity, pricePerUnit));
        } else {
            newPositions.put(symbol, new Position(symbol, quantity, pricePerUnit));
        }

        return new Portfolio(simulationId, newPositions, newCash);
    }

    public Portfolio sell(Symbol symbol, BigDecimal quantity, Money pricePerUnit) {
        Position position = positions.get(symbol);
        if (position == null) {
            throw new IllegalStateException("No position for " + symbol);
        }

        Money proceeds = pricePerUnit.multiply(quantity);
        Money newCash = cashAvailable.add(proceeds);
        Map<Symbol, Position> newPositions = new HashMap<>(positions);

        Position updatedPosition = position.reduceQuantity(quantity);
        if (updatedPosition.quantity().compareTo(BigDecimal.ZERO) == 0) {
            newPositions.remove(symbol);
        } else {
            newPositions.put(symbol, updatedPosition);
        }

        return new Portfolio(simulationId, newPositions, newCash);
    }

    public Money calculateTotalValue(Map<Symbol, Money> currentPrices) {
        Money positionsValue = positions.values().stream()
            .map(p -> {
                // Use current price if available, otherwise fall back to average price
                Money price = currentPrices.get(p.symbol());
                if (price == null) {
                    // Fallback: use the position's average price (cost basis)
                    price = p.averagePrice();
                }
                return p.calculateValue(price);
            })
            .reduce(Money.ZERO, Money::add);

        return cashAvailable.add(positionsValue);
    }
}
