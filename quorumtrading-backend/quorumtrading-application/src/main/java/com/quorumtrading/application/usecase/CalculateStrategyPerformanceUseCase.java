package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.CalculateStrategyPerformanceQuery;
import com.quorumtrading.model.valueobject.StrategyPerformance;

/**
 * Use case for calculating aggregate performance metrics for a trading strategy.
 */
public interface CalculateStrategyPerformanceUseCase {
    StrategyPerformance execute(CalculateStrategyPerformanceQuery query);
}
