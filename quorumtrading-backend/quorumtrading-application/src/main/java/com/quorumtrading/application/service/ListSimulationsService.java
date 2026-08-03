package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.ListSimulationsUseCase;
import com.quorumtrading.application.usecase.query.ListSimulationsQuery;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.enums.SimulationStatus;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service implementation for listing simulations.
 * Framework-agnostic, uses constructor injection.
 */
public class ListSimulationsService implements ListSimulationsUseCase {

    private final SimulationRepository simulationRepository;

    public ListSimulationsService(SimulationRepository simulationRepository) {
        this.simulationRepository = simulationRepository;
    }

    @Override
    public List<Simulation> execute(ListSimulationsQuery query) {
        // A null caller used to fall through to the unscoped branch below, returning every
        // user's simulations. Reject instead; the platform's own callers pass UserId.SYSTEM.
        if (query.requestingUserId() == null) {
            throw new AccessDeniedException(null, "Simulation", "list");
        }
        if (!query.requestingUserId().isSystem()) {
            List<Simulation> simulations = simulationRepository.findByOwnerId(query.requestingUserId());

            // Apply status filter if provided
            if (query.status().isPresent()) {
                SimulationStatus statusEnum = SimulationStatus.valueOf(query.status().get().toUpperCase());
                simulations = simulations.stream()
                    .filter(s -> s.getStatus() == statusEnum)
                    .collect(Collectors.toList());
            }

            // Apply strategyId filter if provided
            if (query.strategyId().isPresent()) {
                simulations = simulations.stream()
                    .filter(s -> s.getStrategyId().equals(query.strategyId().get()))
                    .collect(Collectors.toList());
            }

            // Apply limit if provided
            if (query.limit().isPresent()) {
                int limit = query.limit().get();
                if (limit > 0 && simulations.size() > limit) {
                    simulations = simulations.subList(0, limit);
                }
            }

            return simulations;
        }

        // Legacy behavior: no user scoping
        // If no filters are provided, use findAll for backwards compatibility
        if (query.status().isEmpty() && query.strategyId().isEmpty() &&
            query.since().isEmpty() && query.limit().isEmpty()) {
            return simulationRepository.findAll();
        }

        // Parse status string to enum if provided
        Optional<SimulationStatus> statusEnum = query.status()
            .map(String::toUpperCase)
            .map(SimulationStatus::valueOf);

        // Use findWithFilters for filtered queries
        return simulationRepository.findWithFilters(
            statusEnum,
            query.strategyId(),
            query.since(),
            query.limit()
        );
    }
}
