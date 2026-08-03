package com.quorumtrading.application.service;

import com.quorumtrading.application.event.SimulationCompletedEvent;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.model.entity.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Service that analyzes simulation results and updates strategy metrics.
 *
 * <p><b>DDD Classification:</b> Application Service</p>
 * Orchestrates the results analysis workflow.
 *
 * <p><b>Event-Driven:</b></p>
 * Called by SimulationCompletedHandler, updates strategy with new metrics.
 */
public class ResultsAnalyzerService {

    private static final Logger LOG = LoggerFactory.getLogger(ResultsAnalyzerService.class);

    private final StrategyRepository strategyRepository;
    private final StrategyHealthCheckerService healthChecker;

    public ResultsAnalyzerService(
            StrategyRepository strategyRepository,
            StrategyHealthCheckerService healthChecker) {
        this.strategyRepository = strategyRepository;
        this.healthChecker = healthChecker;
    }

    /**
     * Analyze completed simulation and update strategy metrics.
     *
     * @param event The simulation completed event
     */
    public void analyzeResults(SimulationCompletedEvent event) {
        LOG.info("Analyzing results for simulation: {}", event.simulationId());

        Strategy strategy = strategyRepository.findById(event.strategyId())
            .orElseThrow(() -> new IllegalStateException("Strategy not found: " + event.strategyId()));

        // Use the contribution-aware return computed by the completion publisher
        // (EndSimulationService / ForceEndSimulationService) rather than recomputing
        // a naive (final − initialCash)/initialCash that would double-count DCA top-ups.
        BigDecimal returnPercent = event.totalReturn() != null ? event.totalReturn() : BigDecimal.ZERO;

        LOG.info("Simulation {} completed with {}% return", event.simulationId(), returnPercent);

        healthChecker.checkStrategyHealth(strategy, returnPercent);
    }
}
