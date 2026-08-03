package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ListPublicStrategiesUseCase;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.StrategyId;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Service implementation for listing all publicly visible strategies.
 * Framework-agnostic, uses constructor injection.
 */
public class ListPublicStrategiesService implements ListPublicStrategiesUseCase {

    private final StrategyRepository strategyRepository;

    public ListPublicStrategiesService(StrategyRepository strategyRepository) {
        this.strategyRepository = strategyRepository;
    }

    @Override
    public List<Strategy> execute() {
        return strategyRepository.findAllPublic();
    }

    @Override
    public Strategy findPublicById(StrategyId id) {
        Strategy strategy = strategyRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + id));
        if (!strategy.isPublicVisible() || !strategy.isActive()) {
            throw new NoSuchElementException("Strategy not found: " + id);
        }
        return strategy;
    }
}
