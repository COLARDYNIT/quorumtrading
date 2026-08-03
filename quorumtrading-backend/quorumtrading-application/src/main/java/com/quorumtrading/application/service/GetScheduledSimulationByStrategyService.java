package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.ScheduledSimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.GetScheduledSimulationByStrategyUseCase;
import com.quorumtrading.model.entity.ScheduledSimulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Service implementation for retrieving scheduled simulations by strategy.
 * Framework-agnostic, uses constructor injection.
 */
public class GetScheduledSimulationByStrategyService implements GetScheduledSimulationByStrategyUseCase {

    private final ScheduledSimulationRepository scheduledSimulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public GetScheduledSimulationByStrategyService(ScheduledSimulationRepository scheduledSimulationRepository,
                                                    StrategyRepository strategyRepository,
                                                    AuthorizationService authorizationService) {
        this.scheduledSimulationRepository = scheduledSimulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Optional<ScheduledSimulation> execute(StrategyId strategyId, UserId requestingUserId) {
        // Authorization check
        Strategy strategy = strategyRepository.findById(strategyId)
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + strategyId));
        authorizationService.requireView(requestingUserId, strategy);

        return scheduledSimulationRepository.findByStrategyId(strategyId);
    }
}
