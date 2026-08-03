package com.quorumtrading.model.entity;

import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Domain entity representing a simulation's portfolio within a shared broker account.
 * Tracks positions, cash, and realized/unrealized P&L for a single simulation.
 *
 * <p><b>Per-Simulation Tracking:</b></p>
 * <ul>
 *   <li>Each simulation has its own SimulationPortfolio</li>
 *   <li>Multiple simulations can share a BrokerAccount</li>
 *   <li>P&L is tracked independently per simulation</li>
 * </ul>
 *
 * <p><b>Reserved Capital Model:</b></p>
 * <ul>
 *   <li>cashReserved: The original trading limit allocated to this simulation</li>
 *   <li>cashAvailable: Remaining cash available for new trades</li>
 *   <li>On buy: cash decreases by total cost</li>
 *   <li>On sell: cash increases by proceeds, realized P&L updated</li>
 * </ul>
 *
 * <p><b>P&L Calculation:</b></p>
 * <ul>
 *   <li>Realized P&L: Accumulated profit/loss from closed positions (sell price - cost basis)</li>
 *   <li>Unrealized P&L: Current market value of open positions minus their cost basis</li>
 * </ul>
 *
 * @see BrokerAccount
 * @see Position
 */
public class SimulationPortfolio {

    private final SimulationId simulationId;
    private final BrokerAccountId brokerAccountId;
    private final Map<Symbol, Position> positions;
    private Money cashAvailable;
    private Money cashReserved;
    private Money realizedPnL;

    /**
     * Public constructor for persistence layer reconstruction.
     * Use {@link #initialize(SimulationId, BrokerAccountId, Money)} for creating new portfolios.
     *
     * @param simulationId the simulation this portfolio belongs to
     * @param brokerAccountId the broker account this portfolio is linked to
     * @param positions the current positions (defensive copy taken)
     * @param cashAvailable the remaining cash available for trading
     * @param cashReserved the original trading limit (immutable)
     * @param realizedPnL the accumulated realized profit/loss
     */
    public SimulationPortfolio(
        SimulationId simulationId,
        BrokerAccountId brokerAccountId,
        Map<Symbol, Position> positions,
        Money cashAvailable,
        Money cashReserved,
        Money realizedPnL
    ) {
        this.simulationId = Objects.requireNonNull(simulationId, "simulationId cannot be null");
        this.brokerAccountId = Objects.requireNonNull(brokerAccountId, "brokerAccountId cannot be null");
        this.positions = new HashMap<>(Objects.requireNonNull(positions, "positions cannot be null"));
        this.cashAvailable = Objects.requireNonNull(cashAvailable, "cashAvailable cannot be null");
        this.cashReserved = Objects.requireNonNull(cashReserved, "cashReserved cannot be null");
        this.realizedPnL = Objects.requireNonNull(realizedPnL, "realizedPnL cannot be null");
    }

    /**
     * Creates a new SimulationPortfolio initialized with a trading limit.
     * The trading limit becomes both the available cash and the reserved amount.
     *
     * @param simulationId the simulation this portfolio belongs to
     * @param brokerAccountId the broker account this portfolio is linked to
     * @param tradingLimit the capital allocated to this simulation
     * @return a new SimulationPortfolio ready for trading
     */
    public static SimulationPortfolio initialize(
        SimulationId simulationId,
        BrokerAccountId brokerAccountId,
        Money tradingLimit
    ) {
        return new SimulationPortfolio(
            simulationId,
            brokerAccountId,
            new HashMap<>(),
            tradingLimit,
            tradingLimit,
            Money.zero(tradingLimit.currency())
        );
    }

    // Business methods

    /**
     * Records a buy trade, updating positions and reducing available cash.
     *
     * @param symbol the asset symbol
     * @param quantity the quantity purchased
     * @param pricePerUnit the price per unit
     * @param totalCost the total cost including any fees (must be subtracted from cash)
     * @throws IllegalStateException if insufficient cash available
     */
    public void recordBuy(Symbol symbol, BigDecimal quantity, Money pricePerUnit, Money totalCost) {
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(pricePerUnit, "pricePerUnit cannot be null");
        Objects.requireNonNull(totalCost, "totalCost cannot be null");

        if (totalCost.compareTo(cashAvailable) > 0) {
            throw new IllegalStateException(
                String.format("Insufficient cash. Required: %s, Available: %s", totalCost, cashAvailable)
            );
        }

        // Update or create position
        Position existingPosition = positions.get(symbol);
        if (existingPosition != null) {
            positions.put(symbol, existingPosition.addQuantity(quantity, pricePerUnit));
        } else {
            positions.put(symbol, new Position(symbol, quantity, pricePerUnit));
        }

        // Reduce available cash
        cashAvailable = cashAvailable.subtract(totalCost);
    }

    /**
     * Records a sell trade, updating positions, increasing cash, and calculating realized P&L.
     *
     * @param symbol the asset symbol
     * @param quantity the quantity sold
     * @param pricePerUnit the price per unit
     * @param proceeds the total proceeds from the sale (added to cash)
     * @throws IllegalStateException if no position exists for the symbol
     * @throws IllegalArgumentException if selling more than owned
     */
    public void recordSell(Symbol symbol, BigDecimal quantity, Money pricePerUnit, Money proceeds) {
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(pricePerUnit, "pricePerUnit cannot be null");
        Objects.requireNonNull(proceeds, "proceeds cannot be null");

        Position position = positions.get(symbol);
        if (position == null) {
            throw new IllegalStateException("No position for symbol: " + symbol);
        }

        // Calculate realized P&L for this sale
        // P&L = proceeds - cost basis
        // Cost basis = average price * quantity sold
        Money costBasis = position.averagePrice().multiply(quantity);
        Money tradePnL = proceeds.subtract(costBasis);
        realizedPnL = realizedPnL.add(tradePnL);

        // Update position (this will throw if quantity exceeds available)
        Position updatedPosition = position.reduceQuantity(quantity);

        // Remove position if fully sold, otherwise update
        if (updatedPosition.quantity().compareTo(BigDecimal.ZERO) == 0) {
            positions.remove(symbol);
        } else {
            positions.put(symbol, updatedPosition);
        }

        // Increase available cash
        cashAvailable = cashAvailable.add(proceeds);
    }

    /**
     * Records a cash contribution (recurring-investment top-up). Both
     * {@code cashAvailable} and {@code cashReserved} are increased — the
     * contribution adds to both the available buying power and the
     * total capital basis of this simulation.
     *
     * <p>The matching {@link BrokerAccount#reserve(Money)} call must happen
     * in the same transaction so the parent broker account's reserved
     * balance stays in sync.</p>
     *
     * @param amount the cash being injected (must be positive, same currency)
     * @throws IllegalArgumentException if amount is null or non-positive
     */
    public void recordContribution(Money amount) {
        Objects.requireNonNull(amount, "amount cannot be null");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException(
                    "Contribution amount must be positive, got " + amount.format());
        }
        this.cashAvailable = cashAvailable.add(amount);
        this.cashReserved = cashReserved.add(amount);
    }

    /**
     * Calculates unrealized P&L based on current market prices.
     * Unrealized P&L = sum of (current value - cost basis) for all positions.
     *
     * @param currentPrices map of current prices per symbol
     * @return the total unrealized P&L
     */
    public Money calculateUnrealizedPnL(Map<Symbol, Money> currentPrices) {
        Objects.requireNonNull(currentPrices, "currentPrices cannot be null");

        return positions.values().stream()
            .map(position -> {
                Money currentPrice = currentPrices.get(position.symbol());
                if (currentPrice == null) {
                    // If no current price, assume no unrealized P&L for this position
                    return Money.zero(cashReserved.currency());
                }
                // Current value - cost basis
                Money currentValue = currentPrice.multiply(position.quantity());
                Money costBasis = position.averagePrice().multiply(position.quantity());
                return currentValue.subtract(costBasis);
            })
            .reduce(Money.zero(cashReserved.currency()), Money::add);
    }

    /**
     * Calculates the total portfolio value (cash + position values at current prices).
     *
     * @param currentPrices map of current prices per symbol
     * @return the total portfolio value
     */
    public Money calculateTotalValue(Map<Symbol, Money> currentPrices) {
        Objects.requireNonNull(currentPrices, "currentPrices cannot be null");

        Money positionsValue = positions.values().stream()
            .map(position -> {
                Money currentPrice = currentPrices.get(position.symbol());
                if (currentPrice == null) {
                    // If no current price, use cost basis (average price)
                    currentPrice = position.averagePrice();
                }
                return position.calculateValue(currentPrice);
            })
            .reduce(Money.zero(cashReserved.currency()), Money::add);

        return cashAvailable.add(positionsValue);
    }

    // Getters

    public SimulationId getSimulationId() {
        return simulationId;
    }

    public BrokerAccountId getBrokerAccountId() {
        return brokerAccountId;
    }

    /**
     * Returns an unmodifiable view of current positions.
     *
     * @return unmodifiable map of symbol to position
     */
    public Map<Symbol, Position> getPositions() {
        return Collections.unmodifiableMap(positions);
    }

    public Money getCashAvailable() {
        return cashAvailable;
    }

    public Money getCashReserved() {
        return cashReserved;
    }

    public Money getRealizedPnL() {
        return realizedPnL;
    }

    @Override
    public String toString() {
        return "SimulationPortfolio{" +
            "simulationId=" + simulationId +
            ", brokerAccountId=" + brokerAccountId +
            ", positions=" + positions.size() +
            ", cashAvailable=" + cashAvailable +
            ", cashReserved=" + cashReserved +
            ", realizedPnL=" + realizedPnL +
            '}';
    }
}
