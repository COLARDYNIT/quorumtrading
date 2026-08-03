package com.quorumtrading.application.service;

import com.quorumtrading.application.exception.AccessDeniedException;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ListStrategiesUseCase;
import com.quorumtrading.application.usecase.query.ListStrategiesQuery;
import com.quorumtrading.model.entity.Strategy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for listing strategies.
 * Framework-agnostic, uses constructor injection.
 */
public class ListStrategiesService implements ListStrategiesUseCase {

    private final StrategyRepository strategyRepository;

    public ListStrategiesService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    @Override
    public List<Strategy> execute(ListStrategiesQuery query) {
        List<Strategy> strategies;

        // An absent user is rejected rather than served every strategy in the system. This
        // returned findAll() for a null caller, so an adapter that did not resolve the user
        // handed out every user's strategy content instead of failing.
        if (query.requestingUserId() == null) {
            throw new AccessDeniedException(null, "Strategy", "list");
        }
        if (query.requestingUserId().isSystem()) {
            strategies = strategyRepository.findAll();
        } else {
            strategies = new java.util.ArrayList<>(strategyRepository.findByOwnerId(query.requestingUserId()));
        }

        // Apply name filter if provided
        if (query.name().isPresent()) {
            String nameFilter = query.name().get().toLowerCase();
            strategies = strategies.stream()
                .filter(strategy -> strategy.getName().toLowerCase().contains(nameFilter))
                .collect(Collectors.toList());
        }

        // Apply limit if provided
        if (query.limit().isPresent()) {
            int limit = query.limit().get();
            if (limit > 0 && strategies.size() > limit) {
                strategies = strategies.subList(0, limit);
            }
        }

        return strategies;
    }
}
