package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.usecase.CalculateBenchmarkOverlaysUseCase;
import com.quorumtrading.application.usecase.command.CalculateBenchmarkOverlaysCommand;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.OverlayResult;
import com.quorumtrading.model.valueobject.Symbol;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes buy-and-hold reference overlays for a benchmark.
 *
 * <p>For each requested symbol, delegates to {@link BuyAndHoldOverlayCalculator}
 * to compute the equity curve over the benchmark's date range.</p>
 */
public class CalculateBenchmarkOverlaysService implements CalculateBenchmarkOverlaysUseCase {

    private static final Logger LOG = Logger.getLogger(CalculateBenchmarkOverlaysService.class);

    private final BenchmarkRepository benchmarkRepository;
    private final SimulationRepository simulationRepository;
    private final AuthorizationService authorizationService;
    private final BuyAndHoldOverlayCalculator calculator;

    public CalculateBenchmarkOverlaysService(
            BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            AuthorizationService authorizationService,
            BuyAndHoldOverlayCalculator calculator) {
        this.benchmarkRepository = benchmarkRepository;
        this.simulationRepository = simulationRepository;
        this.authorizationService = authorizationService;
        this.calculator = calculator;
    }

    @Override
    public List<OverlayResult> execute(CalculateBenchmarkOverlaysCommand command) {
        Benchmark benchmark = benchmarkRepository.findById(command.benchmarkId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Benchmark not found: " + command.benchmarkId().value()));

        // The command has always carried a userId; this service never read it, so any caller
        // could read another user's benchmark date range and initial capital (the latter
        // resolved from their simulations below).
        authorizationService.requireView(command.userId(), benchmark);

        Instant startDate = benchmark.getStartDate();
        Instant endDate = benchmark.getEndDate() != null ? benchmark.getEndDate() : Instant.now();

        Money capital = command.initialCapital();
        if (capital == null) {
            capital = resolveDefaultCapital(command);
        }

        String resolution = command.resolution() != null ? command.resolution() : "1d";

        List<OverlayResult> results = new ArrayList<>();
        for (Symbol symbol : command.symbols()) {
            results.add(calculator.computeOverlay(symbol, startDate, endDate, capital.amount(), resolution));
        }

        return results;
    }

    private Money resolveDefaultCapital(CalculateBenchmarkOverlaysCommand command) {
        List<Simulation> simulations = simulationRepository.findByBenchmarkId(command.benchmarkId());
        if (!simulations.isEmpty()) {
            Money initialCash = simulations.get(0).getInitialCash();
            if (initialCash != null) {
                return initialCash;
            }
        }
        return Money.usd(100_000);
    }
}
