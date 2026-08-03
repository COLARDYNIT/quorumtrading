package com.quorumtrading.bootstrap.handler;

import com.quorumtrading.application.event.SimulationCompletedEvent;
import com.quorumtrading.application.service.ResultsAnalyzerService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI handler that observes SimulationCompletedEvent and triggers results analysis.
 *
 * <p><b>Hexagonal Architecture:</b> Bootstrap/Handler Adapter</p>
 * Wires CDI events to application service calls.
 *
 * <p><b>Phase 4 Automation Loop:</b></p>
 * Part of the event-driven pipeline:
 * SimulationCompleted → ResultsAnalyzer → StrategyHealthChecker
 */
@ApplicationScoped
public class SimulationCompletedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(SimulationCompletedHandler.class);

    @Inject
    ResultsAnalyzerService resultsAnalyzerService;

    public void onSimulationCompleted(@ObservesAsync SimulationCompletedEvent event) {
        LOG.info("Received SimulationCompletedEvent for simulation: {}", event.simulationId());
        try {
            resultsAnalyzerService.analyzeResults(event);
        } catch (Exception e) {
            LOG.error("Failed to analyze simulation results: {}", event.simulationId(), e);
            // Don't rethrow - allow other observers to process
        }
    }
}
