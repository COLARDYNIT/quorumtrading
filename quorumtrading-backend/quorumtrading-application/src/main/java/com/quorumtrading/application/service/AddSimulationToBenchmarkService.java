package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.port.out.SimulationRepository;
import com.quorumtrading.application.port.out.StrategyRepository;
import com.quorumtrading.application.usecase.AddSimulationToBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.AddSimulationToBenchmarkCommand;
import com.quorumtrading.model.entity.Benchmark;
import com.quorumtrading.model.entity.Simulation;
import com.quorumtrading.model.entity.Strategy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Service implementation for adding an existing simulation to an existing benchmark.
 * Validates that the simulation's initial capital and date range match the benchmark's existing simulations.
 */
public class AddSimulationToBenchmarkService implements AddSimulationToBenchmarkUseCase {

    private final BenchmarkRepository benchmarkRepository;
    private final SimulationRepository simulationRepository;
    private final StrategyRepository strategyRepository;
    private final AuthorizationService authorizationService;

    public AddSimulationToBenchmarkService(
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
    public void execute(AddSimulationToBenchmarkCommand command) {
        Benchmark benchmark = benchmarkRepository.findById(command.benchmarkId())
            .orElseThrow(() -> new NoSuchElementException("Benchmark not found: " + command.benchmarkId().value()));

        authorizationService.requireDelete(command.userId(), benchmark);

        Simulation simulation = simulationRepository.findById(command.simulationId())
            .orElseThrow(() -> new NoSuchElementException("Simulation not found: " + command.simulationId().value()));

        Strategy strategy = strategyRepository.findById(simulation.getStrategyId())
            .orElseThrow(() -> new NoSuchElementException("Strategy not found: " + simulation.getStrategyId().value()));

        authorizationService.requireEdit(command.userId(), simulation, strategy);

        List<Simulation> existingSimulations = simulationRepository.findByBenchmarkId(command.benchmarkId());
        if (!existingSimulations.isEmpty()) {
            Simulation reference = existingSimulations.get(0);
            validateMatchingParameters(simulation, reference);
        }

        simulation.assignToBenchmark(command.benchmarkId()); // domain validation
        simulationRepository.updateBenchmarkId(command.simulationId(), command.benchmarkId());
    }

    private void validateMatchingParameters(Simulation candidate, Simulation reference) {
        if (!Objects.equals(candidate.getInitialCash(), reference.getInitialCash())) {
            throw new IllegalArgumentException(
                String.format("Cannot add: simulation has %s initial capital but benchmark uses %s",
                    candidate.getInitialCash(), reference.getInitialCash()));
        }

        if (!Objects.equals(candidate.getStartDate(), reference.getStartDate())) {
            throw new IllegalArgumentException(
                String.format("Cannot add: simulation starts %s but benchmark starts %s",
                    candidate.getStartDate(), reference.getStartDate()));
        }

        if (!Objects.equals(candidate.getEndDate(), reference.getEndDate())) {
            throw new IllegalArgumentException(
                String.format("Cannot add: simulation ends %s but benchmark ends %s",
                    candidate.getEndDate(), reference.getEndDate()));
        }
    }
}
