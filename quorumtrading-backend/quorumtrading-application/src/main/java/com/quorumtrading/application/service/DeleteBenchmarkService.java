package com.quorumtrading.application.service;

import com.quorumtrading.application.port.out.BenchmarkRepository;
import com.quorumtrading.application.usecase.DeleteBenchmarkUseCase;
import com.quorumtrading.application.usecase.command.DeleteBenchmarkCommand;
import com.quorumtrading.model.entity.Benchmark;

import java.util.NoSuchElementException;

/**
 * Service implementation for deleting benchmarks.
 * Framework-agnostic, uses constructor injection.
 * Relies on database CASCADE DELETE constraints to handle associated data.
 */
public class DeleteBenchmarkService implements DeleteBenchmarkUseCase {

    private final BenchmarkRepository benchmarkRepository;
    private final AuthorizationService authorizationService;

    public DeleteBenchmarkService(BenchmarkRepository benchmarkRepository, AuthorizationService authorizationService) {
        this.benchmarkRepository = benchmarkRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public void execute(DeleteBenchmarkCommand command) {
        Benchmark benchmark = benchmarkRepository.findById(command.benchmarkId())
            .orElseThrow(() -> new NoSuchElementException("Benchmark not found: " + command.benchmarkId()));

        authorizationService.requireDelete(command.requestingUserId(), benchmark);

        if (!benchmarkRepository.deleteById(command.benchmarkId())) {
            throw new NoSuchElementException("Benchmark not found: " + command.benchmarkId());
        }
    }
}
