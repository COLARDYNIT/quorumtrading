package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CalculateSimulationOverlaysUseCase;
import com.quorumtrading.application.usecase.command.CalculateSimulationOverlaysCommand;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes buy-and-hold reference overlays for a simulation.
 *
 * <p>Uses the simulation's own date range and initial cash, then delegates
 * to {@link BuyAndHoldOverlayCalculator} for each requested symbol.</p>
 */
public class CalculateSimulationOverlaysService implements CalculateSimulationOverlaysUseCase {

    private static final Logger LOG = Logger.getLogger(CalculateSimulationOverlaysService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;
    private final BuyAndHoldOverlayCalculator calculator;

    public CalculateSimulationOverlaysService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService,
            BuyAndHoldOverlayCalculator calculator) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
        this.calculator = calculator;
    }

    @Override
    public List<OverlayResult> execute(CalculateSimulationOverlaysCommand command) {
        Simulation simulation = simulationRepository.findById(command.simulationId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Simulation not found: " + command.simulationId().value()));

        // The command has always carried a userId; this service never read it, so the call site
        // read as authorized in review while any caller could see another user's date range and
        // initial cash.
        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Strategy not found for simulation: " + command.simulationId().value()));
        authorizationService.requireView(command.userId(), simulation, strategy);

        Instant startDate = simulation.getStartDate();
        Instant endDate = simulation.getEndDate() != null ? simulation.getEndDate() : Instant.now();

        var capital = simulation.getInitialCash().amount();
        String resolution = command.resolution() != null ? command.resolution() : "1d";

        LOG.debugf("Computing overlays for simulation %s (%s to %s, capital=%s)",
            command.simulationId().value(), startDate, endDate, capital);

        List<OverlayResult> results = new ArrayList<>();
        for (Symbol symbol : command.symbols()) {
            results.add(calculator.computeOverlay(symbol, startDate, endDate, capital, resolution));
        }

        return results;
    }
}
