package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.Symbol;
import java.math.BigDecimal;

/**
 * Represents a position in a trading portfolio.
 * Tracks the quantity of an asset held, the weighted average cost basis, and optionally the current market price.
 *
 * Immutable entity - all mutations return new instances.
 *
 * @param symbol the asset symbol
 * @param quantity the quantity held
 * @param averagePrice the weighted average cost basis (what we paid)
 * @param currentPrice the current market price at snapshot time (optional, can be null for backward compatibility)
 */
public record Position(Symbol symbol, BigDecimal quantity, Money averagePrice, Money currentPrice) {

    /**
     * Backward compatibility constructor without currentPrice.
     */
    public Position(Symbol symbol, BigDecimal quantity, Money averagePrice) {
        this(symbol, quantity, averagePrice, null);
    }

    public Position {
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol cannot be null");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
        if (averagePrice == null) {
            throw new IllegalArgumentException("Average price cannot be null");
        }
        // currentPrice is optional (can be null for backward compatibility)
    }

    /**
     * Creates an empty position with zero quantity and zero average price.
     *
     * @param symbol the asset symbol
     * @return an empty position
     */
    public static Position empty(Symbol symbol) {
        return new Position(symbol, BigDecimal.ZERO, Money.ZERO);
    }

    /**
     * Adds quantity to the position with weighted average cost calculation.
     *
     * Formula: newAvgPrice = (currentAvgPrice * currentQty + newPrice * newQty) / (currentQty + newQty)
     *
     * @param qty the quantity to add (must be positive)
     * @param pricePerUnit the price per unit for the added quantity
     * @return a new Position with updated quantity and average price (currentPrice remains null)
     * @throws IllegalArgumentException if qty is null or non-positive
     */
    public Position addQuantity(BigDecimal qty, Money pricePerUnit) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to add must be positive");
        }

        // Calculate weighted average cost
        Money totalCost = averagePrice.multiply(quantity).add(pricePerUnit.multiply(qty));
        BigDecimal newQuantity = quantity.add(qty);
        Money newAveragePrice = totalCost.divide(newQuantity);

        return new Position(symbol, newQuantity, newAveragePrice);
    }

    /**
     * Reduces the quantity held in the position.
     * Average price remains unchanged (for tax reporting purposes).
     *
     * @param qty the quantity to reduce (must be positive and <= current quantity)
     * @return a new Position with reduced quantity
     * @throws IllegalArgumentException if qty is invalid or exceeds current quantity
     */
    public Position reduceQuantity(BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity to reduce must be positive");
        }
        if (quantity.compareTo(qty) < 0) {
            throw new IllegalArgumentException(
                "Cannot reduce quantity by " + qty + " (only " + quantity + " available)"
            );
        }

        BigDecimal newQuantity = quantity.subtract(qty);
        return new Position(symbol, newQuantity, averagePrice);  // Average price unchanged
    }

    /**
     * Calculates the current market value of the position.
     *
     * @param currentPrice the current market price per unit
     * @return the total value (currentPrice * quantity)
     */
    public Money calculateValue(Money currentPrice) {
        return currentPrice.multiply(quantity);
    }
}
