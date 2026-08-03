package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.DeletePausedSimulationsUseCase;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.UserId;

import java.util.List;

/**
 * Service implementation for deleting all paused simulations.
 * Framework-agnostic, uses constructor injection.
 * Bean wired via @Produces in ApplicationConfig (no CDI annotations).
 */
public class DeletePausedSimulationsService implements DeletePausedSimulationsUseCase {

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public DeletePausedSimulationsService(SimulationRepository simulationRepository,
                                          StrategyRepository strategyRepository,
                                          AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public int execute(UserId requestingUserId) {
        // Find all paused simulations
        List<Simulation> pausedSimulations = simulationRepository.findPausedSimulations();

        // Delete each paused simulation (only those owned by the requesting user)
        int deletedCount = 0;
        for (Simulation simulation : pausedSimulations) {
            Strategy strategy = strategyRepository.findById(simulation.getStrategyId()).orElse(null);
            if (strategy == null || !authorizationService.canDelete(requestingUserId, simulation, strategy)) {
                continue;
            }
            if (simulationRepository.deleteById(simulation.getSimulationId())) {
                deletedCount++;
            }
        }

        return deletedCount;
    }
}
