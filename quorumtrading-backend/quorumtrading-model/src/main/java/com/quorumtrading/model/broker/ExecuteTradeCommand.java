package com.quorumtrading.model.broker;

import com.quorumtrading.model.enums.OrderType;
import com.quorumtrading.model.enums.TradeAction;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.Symbol;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Command to execute a trade through the broker.
 * Supports both real-time and backtesting (historical) execution via executionTimestamp.
 */
public record ExecuteTradeCommand(
        SimulationId simulationId,
        TradeAction action,
        Symbol symbol,
        BigDecimal quantity,
        OrderType orderType,
        Money limitPrice,
        Instant executionTimestamp
) {

    public ExecuteTradeCommand {
        Objects.requireNonNull(simulationId, "SimulationId cannot be null");
        Objects.requireNonNull(action, "TradeAction cannot be null");
        Objects.requireNonNull(symbol, "Symbol cannot be null");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(orderType, "OrderType cannot be null");
        Objects.requireNonNull(executionTimestamp, "ExecutionTimestamp cannot be null");

        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (orderType == OrderType.LIMIT && limitPrice == null) {
            throw new IllegalArgumentException("Limit price is required for LIMIT orders");
        }
    }

    /**
     * Get the limit price as an Optional.
     */
    public Optional<Money> getLimitPrice() {
        return Optional.ofNullable(limitPrice);
    }

    /**
     * Create a market order command.
     */
    public static ExecuteTradeCommand market(
            SimulationId simulationId,
            TradeAction action,
            Symbol symbol,
            BigDecimal quantity,
            Instant executionTimestamp) {
        return new ExecuteTradeCommand(
                simulationId,
                action,
                symbol,
                quantity,
                OrderType.MARKET,
                null,
                executionTimestamp
        );
    }

    /**
     * Create a limit order command.
     */
    public static ExecuteTradeCommand limit(
            SimulationId simulationId,
            TradeAction action,
            Symbol symbol,
            BigDecimal quantity,
            Money limitPrice,
            Instant executionTimestamp) {
        return new ExecuteTradeCommand(
                simulationId,
                action,
                symbol,
                quantity,
                OrderType.LIMIT,
                limitPrice,
                executionTimestamp
        );
    }
}
