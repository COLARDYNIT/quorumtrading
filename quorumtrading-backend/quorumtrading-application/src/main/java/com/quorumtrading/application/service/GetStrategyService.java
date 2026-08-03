package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.GetStrategyUseCase;
import com.quorumtrading.application.usecase.query.GetStrategyQuery;
import com.quorumtrading.model.entity.Strategy;

/**
 * Service implementation for retrieving a single strategy.
 * Framework-agnostic, uses constructor injection.
 */
public class GetStrategyService implements GetStrategyUseCase {

    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public GetStrategyService(StrategyRepository strategyRepository, AuthorizationService authorizationService) {
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public Strategy execute(GetStrategyQuery query) {
        Strategy strategy = strategyRepository.findById(query.strategyId())
            .orElseThrow(() -> new IllegalArgumentException("Strategy not found: " + query.strategyId()));

        authorizationService.requireView(query.requestingUserId(), strategy);

        return strategy;
    }
}
