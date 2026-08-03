package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.ComposeBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.ComposeBenchmarkCommand;
import com.quorumtrading.application.usecase.dto.ComposeBenchmarkResponse;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;
import com.quorumtrading.model.valueobject.BenchmarkId;
import com.quorumtrading.model.valueobject.SimulationId;
import com.quorumtrading.model.valueobject.UserId;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service implementation for composing a benchmark from existing simulations.
 * Creates a new benchmark or adds simulations to an existing one.
 */
public class ComposeBenchmarkService implements ComposeBenchmarkUseCase {

    private final BenchmarkRepository benchmarkRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public ComposeBenchmarkService(
            BenchmarkRepository benchmarkRepository,
            SimulationRepository simulationRepository,
            StrategyRepository strategyRepository,
            AuthorizationService authorizationService) {
        this.benchmarkRepository = benchmarkRepository;
        this.simulationRepository = simulationRepository;
        this.strategyRepository = strategyRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public ComposeBenchmarkResponse execute(ComposeBenchmarkCommand command) {
        List<Simulation> simulations = loadAndAuthorize(command.simulationIds(), command.userId());

        BenchmarkId benchmarkId;
        String benchmarkName;

        if (command.benchmarkId() == null) {
            // Reject conflicts before persisting anything. assignToBenchmark rejects a simulation
            // that already belongs to a different benchmark, and that check used to run after the
            // new benchmark row had been saved — so a failed compose left an empty orphan
            // benchmark behind that the caller never learned the id of.
            List<Simulation> conflicting = simulations.stream()
                .filter(s -> s.getBenchmarkId() != null)
                .toList();
            if (!conflicting.isEmpty()) {
                String ids = conflicting.stream()
                    .map(s -> s.getSimulationId().value() + " (in benchmark " + s.getBenchmarkId().value() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
                throw new IllegalStateException(
                    "Cannot compose a new benchmark: these simulations already belong to another "
                        + "benchmark — " + ids
                        + ". Remove them from it first, or pass benchmarkId to add to that benchmark.");
            }

            Benchmark benchmark = Benchmark.fromSimulations(
                new BenchmarkId(UUID.randomUUID()),
                command.name(),
                simulations,
                command.userId()
            );
            benchmarkRepository.save(benchmark);
            benchmarkId = benchmark.getBenchmarkId();
            benchmarkName = benchmark.getName();
        } else {
            Benchmark benchmark = benchmarkRepository.findById(command.benchmarkId())
                .orElseThrow(() -> new NoSuchElementException("Benchmark not found: " + command.benchmarkId().value()));
            authorizationService.requireDelete(command.userId(), benchmark);
            benchmarkId = benchmark.getBenchmarkId();
            benchmarkName = benchmark.getName();
        }

        int addedCount = 0;
        for (Simulation simulation : simulations) {
            if (benchmarkId.equals(simulation.getBenchmarkId())) {
                continue; // Already assigned to this benchmark — idempotent
            }
            simulation.assignToBenchmark(benchmarkId); // domain validation
            simulationRepository.updateBenchmarkId(simulation.getSimulationId(), benchmarkId);
            addedCount++;
        }

        List<Simulation> allInBenchmark = simulationRepository.findByBenchmarkId(benchmarkId);

        return new ComposeBenchmarkResponse(
            benchmarkId.value(),
            benchmarkName,
            allInBenchmark.size(),
            addedCount
        );
    }

    private List<Simulation> loadAndAuthorize(List<SimulationId> simulationIds, UserId userId) {
        List<Simulation> simulations = new ArrayList<>();
        for (SimulationId simId : simulationIds) {
            Simulation simulation = simulationRepository.findById(simId)
                .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + simId.value()));

            Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
                .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + simulation.getStrategyId().value()));

            authorizationService.requireEdit(userId, simulation, strategy);
            simulations.add(simulation);
        }
        return simulations;
    }
}
