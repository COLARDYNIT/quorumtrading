package com.quorumtrading.application.port.out;

import com.quorumtrading.model.broker.ExecuteTradeCommand;
import com.quorumtrading.model.broker.TradeResult;
import com.quorumtrading.model.valueobject.OrderId;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.Optional;

/**
 * Port for persisting MockBrokerAdapter state to the database.
 * Enables horizontal scaling by making mock broker state cluster-safe.
 *
 * <p><b>State Managed:</b></p>
 * <ul>
 *   <li>Order history: Trade results keyed by OrderId</li>
 * </ul>
 *
 * <p><b>Note:</b> Portfolio state (cash, positions) is already persisted
 * via {@link SimulationPortfolioRepository}. This port handles the remaining
 * state that MockBrokerAdapter previously kept only in-memory.</p>
 */
public interface MockBrokerStatePort {

    /**
     * Saves a trade result (order) to persistent storage.
     *
     * @param command the trade command containing symbol, side, quantity
     * @param result the trade result to persist
     */
    void saveOrder(ExecuteTradeCommand command, TradeResult result);

    /**
     * Retrieves a trade result by its order ID.
     *
     * @param orderId the order ID to look up
     * @return the trade result if found, empty otherwise
     */
    Optional<TradeResult> findOrder(OrderId orderId);

    /**
     * Deletes all orders for a simulation.
     * Called when clearing portfolio state.
     *
     * @param simulationId the simulation whose orders should be deleted
     */
    void deleteOrdersBySimulation(SimulationId simulationId);
}
