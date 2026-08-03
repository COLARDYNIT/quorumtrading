package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.CompareSimulationsUseCase;
import com.quorumtrading.application.usecase.command.CompareSimulationsCommand;
import com.quorumtrading.application.usecase.dto.CompareSimulationsResponse;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.StrategyId;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Groups existing simulations into a benchmark for side-by-side comparison.
 * Does NOT create new simulations — re-parents existing ones.
 */
public class CompareSimulationsService implements CompareSimulationsUseCase {

    private static final Logger LOG = Logger.getLogger(CompareSimulationsService.class);

    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final BenchmarkRepository benchmarkRepository;
    private final AuthorizationService authorizationService;

    public CompareSimulationsService(
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            BenchmarkRepository benchmarkRepository,
            AuthorizationService authorizationService) {
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.benchmarkRepository = benchmarkRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public CompareSimulationsResponse execute(CompareSimulationsCommand command) {
        LOG.infof("Comparing %d simulations for user %s", command.simulationIds().size(), command.userId().value());

        // 1. Load and validate all simulations
        List<Simulation> simulations = new ArrayList<>();
        for (UUID simId : command.simulationIds()) {
            Simulation sim = simulationRepository.findById(new SimulationId(simId))
                .orElseThrow(() -> new IllegalArgumentException("Simulation not found: " + simId));

            // Verify ownership via strategy
            Strategy strategy = strategyRepository.findById(sim.getStrategyId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Strategy not found for simulation " + simId + ": " + sim.getStrategyId().value()));
            authorizationService.requireView(command.userId(), sim, strategy);

            simulations.add(sim);
        }

        // 2. Collect warnings about parameter mismatches
        List<String> warnings = collectWarnings(simulations);

        // 3. Compute date range for benchmark
        Instant earliestStart = simulations.stream()
            .map(Simulation::getStartDate)
            .min(Instant::compareTo)
            .orElseThrow();

        boolean anyOpenEnded = simulations.stream().anyMatch(s -> s.getEndDate() == null);
        Instant latestEnd = anyOpenEnded ? null : simulations.stream()
            .map(Simulation::getEndDate)
            .max(Instant::compareTo)
            .orElse(null);

        // 4. Create benchmark
        BenchmarkId benchmarkId = BenchmarkId.generate();
        Benchmark benchmark = new Benchmark(
            benchmarkId,
            command.name(),
            earliestStart,
            latestEnd,
            Instant.now(),
            command.userId());
        benchmarkRepository.save(benchmark);

        // 5. Assign simulations to benchmark
        for (Simulation sim : simulations) {
            sim.assignToBenchmark(benchmarkId);
            simulationRepository.save(sim);
        }

        LOG.infof("Created benchmark %s with %d simulations, %d warnings",
            benchmarkId.value(), simulations.size(), warnings.size());

        return new CompareSimulationsResponse(
            benchmarkId.value(),
            benchmark.getName(),
            command.simulationIds(),
            warnings);
    }

    private List<String> collectWarnings(List<Simulation> simulations) {
        List<String> warnings = new ArrayList<>();
        Simulation first = simulations.get(0);

        // Check start dates
        boolean differentStartDates = simulations.stream()
            .anyMatch(s -> !s.getStartDate().equals(first.getStartDate()));
        if (differentStartDates) {
            warnings.add("Simulations have different start dates");
        }

        // Check end dates
        boolean differentEndDates = simulations.stream()
            .anyMatch(s -> {
                if (first.getEndDate() == null) return s.getEndDate() != null;
                return !first.getEndDate().equals(s.getEndDate());
            });
        if (differentEndDates) {
            warnings.add("Simulations have different end dates");
        }

        // Check initial capital
        boolean differentCapital = simulations.stream()
            .anyMatch(s -> {
                if (first.getInitialCash() == null) return s.getInitialCash() != null;
                return !first.getInitialCash().equals(s.getInitialCash());
            });
        if (differentCapital) {
            warnings.add("Simulations have different initial capital amounts");
        }

        // Check broker types (mock vs real)
        boolean differentBrokerTypes = simulations.stream()
            .anyMatch(s -> {
                boolean firstHasBroker = first.getBrokerAccountId() != null;
                boolean thisHasBroker = s.getBrokerAccountId() != null;
                return firstHasBroker != thisHasBroker;
            });
        if (differentBrokerTypes) {
            warnings.add("Simulations use different broker types (some mock, some real)");
        }

        // Check mixed statuses
        boolean hasCompleted = simulations.stream()
            .anyMatch(s -> s.getStatus() == com.quorumtrading.model.enums.SimulationStatus.COMPLETED);
        boolean hasFailed = simulations.stream()
            .anyMatch(s -> s.getStatus() == com.quorumtrading.model.enums.SimulationStatus.FAILED);
        if (hasCompleted && hasFailed) {
            warnings.add("Benchmark includes both COMPLETED and FAILED simulations");
        }

        return warnings;
    }
}
