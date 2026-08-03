package com.quorumtrading.application.usecase;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.service.AuthorizationService;
import com.quorumtrading.application.usecase.command.ReorderQueueCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.enums.SimulationStatus;
import com.quorumtrading.model.valueobject.SimulationId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Application use case for reordering the simulation queue.
 * Updates the execution order of pending simulations by manipulating their createdAt timestamps.
 *
 * <p>Only PENDING simulations can be reordered. Running or completed simulations are ignored.</p>
 *
 * <p><b>Algorithm:</b></p>
 * <ul>
 *   <li>Validates all IDs exist and are PENDING</li>
 *   <li>Updates createdAt timestamps to maintain desired order (earliest = next to run)</li>
 *   <li>Returns updated list of simulations in new order</li>
 * </ul>
 */
public class ReorderQueueUseCase {

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public ReorderQueueUseCase(SimulationRepository simulationRepository, StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * Executes the reorder queue command.
     *
     * @param command Contains ordered list of simulation IDs
     * @return Updated list of simulations in new order
     * @throws NoSuchElementException if any simulation ID doesn't exist
     * @throws IllegalStateException if any simulation is not in PENDING status
     */
    public List<Simulation> execute(ReorderQueueCommand command) {
        List<SimulationId> orderedIds = command.orderedSimulationIds();

        // Fetch all simulations
        Map<SimulationId, Simulation> simulationMap = orderedIds.stream()
            .map(id -> simulationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + id.value())))
            .collect(Collectors.toMap(Simulation::getSimulationId, Function.identity()));

        // Validate all are PENDING
        simulationMap.values().forEach(sim -> {
            if (sim.getStatus() != SimulationStatus.PENDING) {
                throw new IllegalStateException(
                    "Cannot reorder simulation " + sim.getSimulationId().value() +
                    " with status " + sim.getStatus() + ". Only PENDING simulations can be reordered."
                );
            }
            // Authorization check
            Strategy strategy = strategyRepository.findById(sim.getStrategyId())
                .orElseThrow(() -> new NoSuchElementException("Strategy not found for simulation: " + sim.getSimulationId().value()));
            authorizationService.requireEdit(command.requestingUserId(), sim, strategy);
        });

        // Calculate base timestamp (1 second per position to ensure stable ordering)
        Instant baseTime = Instant.now().minus(orderedIds.size(), ChronoUnit.SECONDS);

        // Update createdAt timestamps to enforce order (earliest first)
        for (int i = 0; i < orderedIds.size(); i++) {
            Simulation sim = simulationMap.get(orderedIds.get(i));
            Instant newCreatedAt = baseTime.plus(i, ChronoUnit.SECONDS);

            // Use reflection or a setter to update createdAt
            // For now, we'll rely on the persistence layer to handle the update
            simulationRepository.updateCreatedAt(sim.getSimulationId(), newCreatedAt);
        }

        // Fetch and return updated simulations
        return orderedIds.stream()
            .map(id -> simulationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + id.value())))
            .collect(Collectors.toList());
    }
}
