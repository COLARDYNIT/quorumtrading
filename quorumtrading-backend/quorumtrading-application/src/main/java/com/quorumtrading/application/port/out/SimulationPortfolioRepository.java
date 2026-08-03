package com.quorumtrading.application.port.out;

import com.quorumtrading.model.entity.SimulationPortfolio;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.SimulationId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for SimulationPortfolio entities.
 * Implemented by persistence adapters to provide data access.
 *
 * <p>This port defines the contract for simulation portfolio persistence operations.
 * The implementation handles mapping between domain entities and JPA entities,
 * including JSONB serialization of positions.</p>
 *
 * <p><b>Key Points:</b></p>
 * <ul>
 *   <li>SimulationId is the primary key (1:1 relationship with Simulation)</li>
 *   <li>Multiple portfolios can reference the same BrokerAccount</li>
 *   <li>Positions are stored as JSONB for flexible schema</li>
 * </ul>
 */
public interface SimulationPortfolioRepository {

    /**
     * Saves a simulation portfolio (creates or updates).
     *
     * @param portfolio the simulation portfolio to save
     * @return the saved portfolio with updated timestamps
     */
    SimulationPortfolio save(SimulationPortfolio portfolio);

    /**
     * Finds a simulation portfolio by its simulation ID.
     *
     * @param simulationId the simulation ID (primary key)
     * @return an Optional containing the portfolio if found, empty otherwise
     */
    Optional<SimulationPortfolio> findById(SimulationId simulationId);

    /**
     * Finds all portfolios linked to a specific broker account.
     * Useful for calculating total reserved balance across simulations.
     *
     * @param brokerAccountId the broker account ID
     * @return a list of portfolios using this broker account
     */
    List<SimulationPortfolio> findByBrokerAccountId(BrokerAccountId brokerAccountId);

    /**
     * Deletes a simulation portfolio by its simulation ID.
     *
     * @param simulationId the simulation ID to delete
     */
    void delete(SimulationId simulationId);

    /**
     * Checks if a simulation portfolio exists by its simulation ID.
     *
     * @param simulationId the simulation ID
     * @return true if the portfolio exists, false otherwise
     */
    boolean existsById(SimulationId simulationId);
}
